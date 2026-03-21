package com.medoq.backend.service;

import com.medoq.backend.entity.Reservation;

/**
 * Sends in-app + push notifications for reservation lifecycle events.
 * The implementation persists to the {@code notifications} table and
 * optionally fires FCM/APNs push through a provider adapter.
 */
public interface NotificationService {

    /** PENDING → CONFIRMED: "Votre réservation est confirmée, venez dans 2h." */
    void sendReservationConfirmed(Reservation reservation);

    /** CONFIRMED/PAID → READY: "Vos médicaments sont prêts." */
    void sendReservationReady(Reservation reservation);

    /** 30 min before expiry: "Votre réservation expire dans 30 min." */
    void sendExpiryWarning(Reservation reservation);

    /** PENDING → EXPIRED: status update for the patient. */
    void sendReservationExpired(Reservation reservation);

    /** ANY → CANCELLED: "Votre réservation a été annulée." */
    void sendReservationCancelled(Reservation reservation);
}
