package com.medoq.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Sliding-window rate limiter backed by Redis.
 *
 * Key    : medoq:rate:{ip}:{epochMinute}
 * Window : 1 minute
 * Limit  : 100 requests / minute / IP
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String KEY_PREFIX = "medoq:rate:";
    private static final int    LIMIT      = 100;
    private static final Duration WINDOW   = Duration.ofMinutes(1);

    private final RedisTemplate<String, String> stringRedisTemplate;

    /**
     * @return true if the request is allowed, false if the limit is exceeded
     */
    public boolean isAllowed(String ip) {
        long minute = System.currentTimeMillis() / 60_000;
        String key  = KEY_PREFIX + ip + ":" + minute;

        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, WINDOW);
        }
        return count == null || count <= LIMIT;
    }

    /** Returns the remaining allowed requests in the current window. */
    public long remaining(String ip) {
        long minute = System.currentTimeMillis() / 60_000;
        String key  = KEY_PREFIX + ip + ":" + minute;
        String val  = stringRedisTemplate.opsForValue().get(key);
        long current = val == null ? 0 : Long.parseLong(val);
        return Math.max(0, LIMIT - current);
    }
}
