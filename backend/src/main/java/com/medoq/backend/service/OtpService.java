package com.medoq.backend.service;

import com.medoq.backend.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Manages 6-digit OTPs stored in Redis.
 *
 * Redis key  : medoq:otp:{phone}
 * Redis value: "{code}:{attempts}"
 * TTL        : 10 minutes
 * Max attempts: 3  (after which the OTP is invalidated)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    private static final String KEY_PREFIX    = "medoq:otp:";
    private static final Duration OTP_TTL     = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS     = 3;
    private static final SecureRandom RANDOM  = new SecureRandom();

    private final RedisTemplate<String, String> stringRedisTemplate;

    // ── Generate & store ──────────────────────────────────────────

    /**
     * Generates a new 6-digit OTP for the given phone, replacing any existing one.
     *
     * @return the generated OTP (to be sent via SMS)
     */
    public String generate(String phone) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(key(phone), encode(code, 0), OTP_TTL);
        log.debug("OTP generated for {}", phone);
        return code;
    }

    // ── Validate ──────────────────────────────────────────────────

    /**
     * Validates the OTP submitted by the user.
     *
     * @throws BusinessException if OTP is expired, already exhausted, or wrong.
     */
    public void validate(String phone, String submittedCode) {
        String stored = stringRedisTemplate.opsForValue().get(key(phone));

        if (stored == null) {
            throw new BusinessException("OTP expired or not requested. Please request a new one.");
        }

        String[] parts   = stored.split(":");
        String savedCode = parts[0];
        int attempts     = Integer.parseInt(parts[1]);

        if (attempts >= MAX_ATTEMPTS) {
            stringRedisTemplate.delete(key(phone));
            throw new BusinessException("OTP invalidated after too many wrong attempts. Please request a new one.");
        }

        if (!savedCode.equals(submittedCode)) {
            int newAttempts = attempts + 1;
            if (newAttempts >= MAX_ATTEMPTS) {
                stringRedisTemplate.delete(key(phone));
                throw new BusinessException("Wrong OTP. Maximum attempts reached. Please request a new one.");
            }
            // Preserve remaining TTL while updating attempt count
            Long ttlSeconds = stringRedisTemplate.getExpire(key(phone));
            Duration remaining = ttlSeconds != null && ttlSeconds > 0
                    ? Duration.ofSeconds(ttlSeconds)
                    : OTP_TTL;
            stringRedisTemplate.opsForValue().set(key(phone), encode(savedCode, newAttempts), remaining);
            throw new BusinessException("Wrong OTP. " + (MAX_ATTEMPTS - newAttempts) + " attempt(s) remaining.");
        }

        // Success — consume the OTP
        stringRedisTemplate.delete(key(phone));
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String key(String phone) {
        return KEY_PREFIX + phone;
    }

    private String encode(String code, int attempts) {
        return code + ":" + attempts;
    }
}
