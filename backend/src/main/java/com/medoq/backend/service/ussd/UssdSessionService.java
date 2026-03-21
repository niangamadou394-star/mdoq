package com.medoq.backend.service.ussd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Stores transient USSD session data in Redis.
 *
 * A USSD session lives at most 3 minutes (Africa's Talking limit).
 * We cache search results and reservation lists so the user can
 * select by index in subsequent steps without re-querying the DB.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UssdSessionService {

    private static final String PREFIX  = "medoq:ussd:";
    private static final Duration TTL   = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    // ── Generic store / load ───────────────────────────────────────────────

    public void store(String sessionId, String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(PREFIX + sessionId + ":" + key, json, TTL);
        } catch (Exception e) {
            log.warn("USSD session store failed: {}", e.getMessage());
        }
    }

    public <T> T load(String sessionId, String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(PREFIX + sessionId + ":" + key);
            return json != null ? objectMapper.readValue(json, type) : null;
        } catch (Exception e) {
            log.warn("USSD session load failed: {}", e.getMessage());
            return null;
        }
    }

    public <T> List<T> loadList(String sessionId, String key, TypeReference<List<T>> ref) {
        try {
            String json = redis.opsForValue().get(PREFIX + sessionId + ":" + key);
            return json != null ? objectMapper.readValue(json, ref) : Collections.emptyList();
        } catch (Exception e) {
            log.warn("USSD session load list failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void clear(String sessionId) {
        // Pattern delete — clean all keys for this session on END
        try {
            var keys = redis.keys(PREFIX + sessionId + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception e) {
            log.warn("USSD session clear failed: {}", e.getMessage());
        }
    }
}
