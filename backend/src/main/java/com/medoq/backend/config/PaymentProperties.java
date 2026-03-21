package com.medoq.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
@ConfigurationProperties(prefix = "medoq")
@Getter @Setter
public class PaymentProperties {

    private Wave    payment  = new Wave();
    private Billing billing  = new Billing();
    private Email   email    = new Email();

    @Getter @Setter
    public static class Wave {
        private WaveProvider    wave   = new WaveProvider();
        private OrangeProvider  orange = new OrangeProvider();
    }

    @Getter @Setter
    public static class WaveProvider {
        private String apiKey;
        private String apiUrl         = "https://api.wave.com/v1";
        private String webhookSecret;
        private String successUrl;
        private String errorUrl;
    }

    @Getter @Setter
    public static class OrangeProvider {
        private String merchantKey;
        private String apiUrl;
        private String notifToken;
        private String returnUrl;
    }

    @Getter @Setter
    public static class Billing {
        /** The date the Medoq platform officially started — drives commission tiers. */
        private LocalDate startDate = LocalDate.of(2024, 1, 1);
    }

    @Getter @Setter
    public static class Email {
        private String from     = "noreply@medoq.sn";
        private String fromName = "Medoq";
    }
}
