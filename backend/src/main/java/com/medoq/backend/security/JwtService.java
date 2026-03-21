package com.medoq.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    private static final String BLACKLIST_PREFIX = "medoq:auth:blacklist:";

    private final SecretKey secretKey;
    private final long expirationMs;
    private final long refreshExpirationMs;
    private final RedisTemplate<String, String> stringRedisTemplate;

    public JwtService(
            @Value("${medoq.jwt.secret}") String secret,
            @Value("${medoq.jwt.expiration}") long expirationMs,
            @Value("${medoq.jwt.refresh-expiration}") long refreshExpirationMs,
            RedisTemplate<String, String> stringRedisTemplate) {
        // Derive a stable 256-bit key from the raw secret string
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Ensure at least 32 bytes; pad or truncate
        byte[] key32 = new byte[32];
        System.arraycopy(keyBytes, 0, key32, 0, Math.min(keyBytes.length, 32));
        this.secretKey = Keys.hmacShaKeyFor(key32);
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ── Token generation ──────────────────────────────────────────

    public String generateAccessToken(UUID userId, String phone, String role) {
        return buildToken(userId, Map.of("phone", phone, "role", role), expirationMs);
    }

    public String generateRefreshToken(UUID userId) {
        return buildToken(userId, Map.of(), refreshExpirationMs);
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    // ── Claims extraction ─────────────────────────────────────────

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    // ── Validation ────────────────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            // Check blacklist
            String jti = claims.getId();
            if (jti != null && isBlacklisted(jti)) {
                log.warn("Token {} is blacklisted", jti);
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    // ── Blacklist (logout) ────────────────────────────────────────

    /**
     * Blacklists a token until its natural expiry.
     * Called on logout to prevent reuse of the access token.
     */
    public void blacklist(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String jti = claims.getId();
            if (jti == null) return;
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining > 0) {
                stringRedisTemplate.opsForValue()
                        .set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(remaining));
            }
        } catch (JwtException e) {
            // Token already invalid — nothing to blacklist
            log.debug("Attempted to blacklist already-invalid token: {}", e.getMessage());
        }
    }

    private boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    // ── Internal builder ─────────────────────────────────────────

    private String buildToken(UUID userId, Map<String, ?> extraClaims, long expiration) {
        String jti = UUID.randomUUID().toString();
        var builder = Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey);

        if (!extraClaims.isEmpty()) {
            builder.claims().add(extraClaims);
        }
        return builder.compact();
    }
}
