package com.medoq.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /** RestClient for Wave API calls. */
    @Bean("waveRestClient")
    public RestClient waveRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept",       "application/json")
                .build();
    }

    /** RestClient for Orange Money API calls. */
    @Bean("orangeRestClient")
    public RestClient orangeRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept",       "application/json")
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return factory;
    }
}
