package com.medoq.backend.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Callback payload sent by Orange Money to POST /api/webhooks/orange-money
 */
public record OrangeCallbackPayload(
    String     status,        // SUCCESS | FAILED | CANCELLED
    String     txnid,         // Orange transaction ID
    BigDecimal amount,
    @JsonProperty("order_id")    String orderId,         // our paymentId
    @JsonProperty("phone_number") String phoneNumber,
    @JsonProperty("notif_token") String notifToken,      // for verification
    String     message
) {
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status);
    }
}
