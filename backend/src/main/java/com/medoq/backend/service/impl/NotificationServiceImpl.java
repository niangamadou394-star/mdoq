package com.medoq.backend.service.impl;

import com.medoq.backend.entity.Notification;
import com.medoq.backend.entity.Notification.Type;
import com.medoq.backend.entity.PharmacyStock;
import com.medoq.backend.entity.Reservation;
import com.medoq.backend.entity.User;
import com.medoq.backend.entity.notification.NotificationTemplate;
import com.medoq.backend.repository.DeviceTokenRepository;
import com.medoq.backend.repository.NotificationRepository;
import com.medoq.backend.service.AtSmsService;
import com.medoq.backend.service.FcmService;
import com.medoq.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final ZoneId DAKAR_TZ = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm").withZone(DAKAR_TZ);

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository  deviceTokenRepository;
    private final FcmService             fcmService;
    private final AtSmsService           atSmsService;

    // ── Public API ─────────────────────────────────────────────────

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReservationConfirmed(Reservation reservation) {
        dispatch(reservation, NotificationTemplate.RESERVATION_CONFIRMED,
            Type.RESERVATION_UPDATE);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReservationReady(Reservation reservation) {
        dispatch(reservation, NotificationTemplate.RESERVATION_READY,
            Type.RESERVATION_UPDATE);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendExpiryWarning(Reservation reservation) {
        dispatch(reservation, NotificationTemplate.RESERVATION_EXPIRING,
            Type.RESERVATION_UPDATE);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReservationExpired(Reservation reservation) {
        String body = String.format(
            "Votre réservation #%s a expiré. Vous pouvez effectuer une nouvelle réservation.",
            reservation.getReference());
        persist(reservation, Type.RESERVATION_UPDATE, "Réservation expirée", body);
        push(reservation, "Réservation expirée", body);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReservationCancelled(Reservation reservation) {
        String body = String.format(
            "Votre réservation #%s a été annulée.%s",
            reservation.getReference(),
            reservation.getCancellationReason() != null
                ? " Motif: " + reservation.getCancellationReason()
                : "");
        persist(reservation, Type.RESERVATION_UPDATE, "Réservation annulée", body);
        push(reservation, "Réservation annulée", body);
    }

    // ── Stock alert ────────────────────────────────────────────────

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendStockAlert(PharmacyStock stock) {
        User owner  = stock.getPharmacy().getOwner();
        String title = NotificationTemplate.STOCK_ALERT.title();
        String body  = String.format(
            "⚠️ Stock critique: %s (%d unités, seuil: %d) — Pharmacie %s",
            stock.getMedication().getName(),
            stock.getQuantity(),
            stock.getReorderLevel(),
            stock.getPharmacy().getName());

        // Persist in-app notification for the owner
        try {
            Notification n = Notification.builder()
                    .user(owner)
                    .type(Type.STOCK_ALERT)
                    .title(title)
                    .body(body)
                    .data(Map.of(
                        "stockId",        stock.getId().toString(),
                        "medicationId",   stock.getMedication().getId().toString(),
                        "medicationName", stock.getMedication().getName(),
                        "quantity",       String.valueOf(stock.getQuantity()),
                        "reorderLevel",   String.valueOf(stock.getReorderLevel())
                    ))
                    .build();
            notificationRepository.save(n);
        } catch (Exception e) {
            log.error("Failed to persist stock alert notification for stock {}: {}",
                stock.getId(), e.getMessage());
        }

        // Push FCM → SMS fallback
        try {
            int pushed = fcmService.sendToUser(owner.getId().toString(), title, body, Map.of(
                "type",    "STOCK_ALERT",
                "stockId", stock.getId().toString()
            ));
            if (pushed == 0) {
                String sms = body.length() <= 160 ? body : body.substring(0, 157) + "...";
                atSmsService.send(owner.getPhone(), sms);
            }
        } catch (Exception e) {
            log.warn("Push/SMS delivery failed for stock alert {}: {}", stock.getId(), e.getMessage());
        }
    }

    // ── Internal ───────────────────────────────────────────────────

    /**
     * Persist in-app notification + push (FCM → SMS fallback).
     */
    private void dispatch(Reservation reservation, NotificationTemplate tpl, Type type) {
        String title = tpl.title();
        String body  = tpl.body(reservation);
        persist(reservation, type, title, body);
        push(reservation, title, body);
    }

    private void persist(Reservation reservation, Type type, String title, String body) {
        try {
            Notification n = Notification.builder()
                    .user(reservation.getCustomer())
                    .type(type)
                    .title(title)
                    .body(body)
                    .data(Map.of(
                        "reservationId", reservation.getId().toString(),
                        "reference",     reservation.getReference(),
                        "pharmacyName",  reservation.getPharmacy().getName(),
                        "status",        reservation.getStatus().name()
                    ))
                    .build();
            notificationRepository.save(n);
            log.debug("Notification saved: [{}] {} → user {}",
                type, title, reservation.getCustomer().getId());
        } catch (Exception e) {
            log.error("Failed to save notification for reservation {}: {}",
                reservation.getId(), e.getMessage());
        }
    }

    /**
     * Try FCM push first; fall back to SMS if the user has no registered tokens.
     */
    private void push(Reservation reservation, String title, String body) {
        try {
            String userId = reservation.getCustomer().getId().toString();
            Map<String, String> data = Map.of(
                "reservationId", reservation.getId().toString(),
                "reference",     reservation.getReference()
            );

            int pushed = fcmService.sendToUser(userId, title, body, data);

            if (pushed == 0) {
                // No FCM tokens — fall back to SMS
                String phone = reservation.getCustomer().getPhone();
                // Truncate to 160 chars for single SMS segment
                String sms = body.length() <= 160 ? body : body.substring(0, 157) + "...";
                atSmsService.send(phone, sms);
                log.debug("SMS fallback sent to {} for reservation {}",
                    phone, reservation.getReference());
            }
        } catch (Exception e) {
            log.warn("Push/SMS delivery failed for reservation {}: {}",
                reservation.getReference(), e.getMessage());
        }
    }
}
