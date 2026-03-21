package com.medoq.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Tracks failed login attempts and locks accounts after 5 failures.
 *
 * Keys:
 *   medoq:auth:fail:{phone}  → integer (auto-expires after 30 min)
 *   medoq:auth:lock:{phone}  → "1"     (TTL 30 min = lock duration)
 */
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private static final String FAIL_PREFIX = "medoq:auth:fail:";
    private static final String LOCK_PREFIX = "medoq:auth:lock:";
    private static final int    MAX_FAILS   = 5;
    private static final Duration LOCK_TTL  = Duration.ofMinutes(30);

    private final RedisTemplate<String, String> stringRedisTemplate;

    public boolean isLocked(String phone) {
        return Boolean.TRUE.equals(
            stringRedisTemplate.hasKey(LOCK_PREFIX + phone));
    }

    /**
     * Records a failed login attempt.
     * Locks the account if MAX_FAILS is reached.
     */
    public void recordFailure(String phone) {
        String failKey = FAIL_PREFIX + phone;
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(failKey, LOCK_TTL);
        }
        if (count != null && count >= MAX_FAILS) {
            stringRedisTemplate.opsForValue().set(LOCK_PREFIX + phone, "1", LOCK_TTL);
            stringRedisTemplate.delete(failKey);
        }
    }

    /** Clears failure counters after a successful login. */
    public void resetFailures(String phone) {
        stringRedisTemplate.delete(FAIL_PREFIX + phone);
    }

    /** Returns remaining lock duration in seconds (0 if not locked). */
    public long lockRemainingSeconds(String phone) {
        Long ttl = stringRedisTemplate.getExpire(LOCK_PREFIX + phone);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
