package com.medoq.backend.scheduler;

import com.medoq.backend.entity.Reservation;
import com.medoq.backend.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Runs every 5 minutes:
 *  1. Expires PENDING reservations past their {@code expires_at}.
 *  2. Sends a 30-minute expiry warning to patients whose reservation
 *     will expire shortly and haven't yet received a warning.
 *
 * Distributed safety: a Redis lock (TTL 4 min) ensures only one instance
 * of the job runs in a multi-pod deployment.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private static final String LOCK_KEY = "medoq:scheduler:lock:reservation-expiry";
    private static final Duration LOCK_TTL = Duration.ofMinutes(4);

    private final ReservationService     reservationService;
    private final StringRedisTemplate    redisTemplate;

    @Scheduled(cron = "0 */5 * * * *")   // every 5 minutes, on the minute
    public void processReservations() {
        // Acquire distributed lock — abort if another instance is already running
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Reservation expiry scheduler skipped (lock held by another instance)");
            return;
        }

        try {
            expireStaleReservations();
            sendExpiryWarnings();
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }

    // ── Step 1 : expire PENDING reservations ─────────────────────

    private void expireStaleReservations() {
        List<Reservation> expired = reservationService.findExpiredPending();

        if (expired.isEmpty()) {
            log.debug("No stale reservations to expire.");
            return;
        }

        log.info("Expiring {} stale reservation(s).", expired.size());
        int count = 0;
        for (Reservation r : expired) {
            try {
                reservationService.expireReservation(r);
                count++;
            } catch (Exception e) {
                // Process all; don't let one failure stop the rest
                log.error("Failed to expire reservation {}: {}", r.getId(), e.getMessage());
            }
        }
        log.info("Expired {}/{} reservation(s).", count, expired.size());
    }

    // ── Step 2 : send 30-min expiry warnings ─────────────────────

    private void sendExpiryWarnings() {
        List<Reservation> aboutToExpire = reservationService.findAboutToExpire();

        if (aboutToExpire.isEmpty()) {
            log.debug("No reservations about to expire.");
            return;
        }

        log.info("Sending expiry warnings for {} reservation(s).", aboutToExpire.size());
        for (Reservation r : aboutToExpire) {
            try {
                reservationService.sendExpiryWarningFor(r);
            } catch (Exception e) {
                log.error("Failed to send expiry warning for reservation {}: {}",
                    r.getId(), e.getMessage());
            }
        }
    }
}
