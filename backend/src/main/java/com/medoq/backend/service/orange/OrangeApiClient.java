package com.medoq.backend.service.orange;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medoq.backend.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Client for the Orange Money WebPay API (Senegal / Sonatel).
 *
 * Flow:
 *   1. Call initiatePayment() → receive payment_token
 *   2. Customer receives USSD push on phone and enters PIN
 *   3. Orange sends callback to our webhook URL
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrangeApiClient {

    private final PaymentProperties props;

    @Qualifier("orangeRestClient")
    private final RestClient restClient;

    // ── DTOs ──────────────────────────────────────────────────────

    public record InitiateRequest(
        @JsonProperty("merchant_key")  String     merchantKey,
        String                          currency,
        @JsonProperty("order_id")      String     orderId,
        BigDecimal                      amount,
        @JsonProperty("return_url")    String     returnUrl,
        @JsonProperty("cancel_url")    String     cancelUrl,
        @JsonProperty("notif_url")     String     notifUrl,
        String                          lang,
        @JsonProperty("reference")     String     reference
    ) {}

    public record InitiateResponse(
        int    status,
        String message,
        Data   data
    ) {
        public record Data(
            @JsonProperty("payment_token") String paymentToken,
            @JsonProperty("payment_url")   String paymentUrl,
            @JsonProperty("notif_token")   String notifToken
        ) {}
    }

    // ── Public API ────────────────────────────────────────────────

    /**
     * Initiates an Orange Money USSD push payment.
     *
     * @param amountXof   amount in FCFA (XOF)
     * @param orderId     our internal payment ID
     * @param reference   reservation reference (for customer display)
     * @return initiation response with payment token
     */
    public InitiateResponse initiatePayment(BigDecimal amountXof,
                                             String orderId,
                                             String reference) {
        var cfg = props.getPayment().getOrange();

        var body = new InitiateRequest(
            cfg.getMerchantKey(),
            "OMP",
            orderId,
            amountXof,
            cfg.getReturnUrl(),
            cfg.getReturnUrl() + "?cancelled=true",
            cfg.getReturnUrl(),
            "fr",
            reference
        );

        log.debug("Initiating Orange Money payment for order: {}", orderId);

        return restClient.post()
                .uri(cfg.getApiUrl() + "/webpayment")
                .header("Authorization", "Bearer " + cfg.getMerchantKey())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new com.medoq.backend.exception.BusinessException(
                        "Orange Money API error: " + resp.getStatusCode());
                })
                .body(InitiateResponse.class);
    }

    /**
     * Verifies the notif_token in the Orange Money callback against
     * the configured token to authenticate the webhook.
     */
    public boolean verifyCallbackToken(String receivedToken) {
        String expected = props.getPayment().getOrange().getNotifToken();
        if (expected == null || expected.isBlank()) return true; // dev mode
        return expected.equals(receivedToken);
    }

    /**
     * Simulates an Orange Money USSD push initiation response for dev/test.
     */
    public Map<String, String> buildUssdPushMessage(String customerPhone, BigDecimal amount) {
        return Map.of(
            "message", String.format(
                "Un paiement de %s FCFA a été demandé. Veuillez confirmer sur votre téléphone %s.",
                amount.toPlainString(), customerPhone),
            "status", "PENDING"
        );
    }
}
