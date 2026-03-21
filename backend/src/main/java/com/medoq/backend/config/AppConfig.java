package com.medoq.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /** Shared RestTemplate for outbound HTTP calls (Africa's Talking SMS, etc.). */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
