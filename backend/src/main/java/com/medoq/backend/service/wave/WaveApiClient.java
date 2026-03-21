package com.medoq.backend.service.wave;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medoq.backend.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Client for the Wave Business API (Senegal).
 * Docs: https://docs.wave.com/business-api
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WaveApiClient {

    private final PaymentProperties props;

    @Qualifier("waveRestClient")
    private final RestClient restClient;

    // ── DTOs ──────────────────────────────────────────────────────

    public record CheckoutRequest(
        BigDecimal amount,
        String     currency,
        @JsonProperty("success_url")     String successUrl,
        @JsonProperty("error_url")       String errorUrl,
        @JsonProperty("client_reference") String clientReference,
        @JsonProperty("business_name")   String businessName
    ) {}

    public record CheckoutSession(
        String     id,
        @JsonProperty("wave_launch_url") String waveLaunchUrl,
        @JsonProperty("checkout_status") String checkoutStatus,
        @JsonProperty("client_reference") String clientReference,
        BigDecimal amount,
        String     currency,
        @JsonProperty("when_expires")    String whenExpires
    ) {}

    // ── Public API ────────────────────────────────────────────────

    /**
     * Creates a Wave checkout session and returns the URL to redirect the customer.
     *
     * @param amountXof      amount in FCFA (XOF)
     * @param paymentIdRef   our internal payment ID used as client_reference
     * @return checkout session from Wave API
     */
    public CheckoutSession createCheckoutSession(BigDecimal amountXof, String paymentIdRef) {
        var body = new CheckoutRequest(
            amountXof, "XOF",
            props.getPayment().getWave().getSuccessUrl(),
            props.getPayment().getWave().getErrorUrl(),
            paymentIdRef,
            "Medoq"
        );

        log.debug("Creating Wave checkout session for payment ref: {}", paymentIdRef);

        return restClient.post()
                .uri(props.getPayment().getWave().getApiUrl() + "/checkout/sessions")
                .header("Authorization", "Bearer " + props.getPayment().getWave().getApiKey())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new com.medoq.backend.exception.BusinessException(
                        "Wave API error: " + response.getStatusCode());
                })
                .body(CheckoutSession.class);
    }
}
