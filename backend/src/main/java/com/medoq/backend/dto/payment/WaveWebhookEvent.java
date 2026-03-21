package com.medoq.backend.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Payload sent by Wave to POST /api/webhooks/wave
 *
 * Wave documentation: https://docs.wave.com/business-api/webhooks
 */
public record WaveWebhookEvent(
    String id,
    String type,
    Data   data,
    String timestamp
) {
    public record Data(
        String     id,
        @JsonProperty("checkout_status")  String     checkoutStatus,
        @JsonProperty("payment_status")   String     paymentStatus,
        @JsonProperty("client_reference") String     clientReference,   // our paymentId
        BigDecimal amount,
        String     currency,
        @JsonProperty("when_completed")   String     whenCompleted,
        @JsonProperty("last_payment_error") String   lastPaymentError
    ) {}

    public boolean isPaymentSucceeded() {
        return data != null
            && "complete".equals(data.checkoutStatus())
            && "succeeded".equals(data.paymentStatus());
    }

    public boolean isPaymentFailed() {
        return data != null
            && ("failed".equals(data.paymentStatus())
                || "cancelled".equals(data.checkoutStatus()));
    }
}
