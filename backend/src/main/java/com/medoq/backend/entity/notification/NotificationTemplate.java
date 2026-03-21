package com.medoq.backend.entity.notification;

import com.medoq.backend.entity.Reservation;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Centralised notification templates for all 5 push/SMS events.
 *
 * Each entry produces a (title, body) pair ready to send via FCM or AT SMS.
 * The body is intentionally ≤160 characters to fit a single SMS segment.
 */
public enum NotificationTemplate {

    RESERVATION_CONFIRMED {
        @Override
        public String title() { return "Réservation confirmée ✅"; }

        @Override
        public String body(Reservation r) {
            String med   = primaryMed(r);
            String pharm = r.getPharmacy().getName();
            String ref   = r.getReference();
            String time  = TIME_FMT.format(r.getExpiresAt());
            return String.format("Votre %s est réservé chez %s. Réf: #%s. Venez avant %s.",
                med, pharm, ref, time);
        }
    },

    RESERVATION_EXPIRING {
        @Override
        public String title() { return "Réservation expire bientôt ⚠️"; }

        @Override
        public String body(Reservation r) {
            String ref   = r.getReference();
            String pharm = r.getPharmacy().getName();
            return String.format(
                "⚠️ Réservation #%s chez %s expire dans 30 min! Confirmez ou annulez.",
                ref, pharm);
        }
    },

    RESERVATION_READY {
        @Override
        public String title() { return "Médicaments prêts à retirer 🏥"; }

        @Override
        public String body(Reservation r) {
            String pharm = r.getPharmacy().getName();
            String ref   = r.getReference();
            return String.format(
                "✅ Vos médicaments sont prêts chez %s. Réf: #%s. Venez les récupérer!",
                pharm, ref);
        }
    },

    PAYMENT_CONFIRMED {
        @Override
        public String title() { return "Paiement reçu 💳"; }

        @Override
        public String body(Reservation r) {
            String ref   = r.getReference();
            String pharm = r.getPharmacy().getName();
            return String.format(
                "Paiement confirmé pour la réservation #%s chez %s. Merci!",
                ref, pharm);
        }
    },

    STOCK_ALERT {
        @Override
        public String title() { return "Alerte stock 🚨"; }

        @Override
        public String body(Reservation r) {
            // Used for pharmacies — r is null in stock alerts; caller builds body directly.
            return "Stock critique détecté. Vérifiez votre tableau de bord Medoq.";
        }
    };

    // ── Abstract methods ──────────────────────────────────────────────────────

    public abstract String title();

    /** Builds the message body from the given reservation context. */
    public abstract String body(Reservation r);

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final ZoneId DAKAR_TZ = ZoneId.of("Africa/Dakar");
    static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm").withZone(DAKAR_TZ);

    static String primaryMed(Reservation r) {
        if (r.getItems().isEmpty()) return "médicament";
        var items = r.getItems();
        String name = items.get(0).getMedication().getName();
        return items.size() == 1 ? name : name + " (+" + (items.size() - 1) + ")";
    }

    /** Truncate body to 160 chars for single-segment SMS. */
    public String smsBody(Reservation r) {
        String full = body(r);
        return full.length() <= 160 ? full : full.substring(0, 157) + "...";
    }
}
