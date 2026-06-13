package com.pranicdoc.cph.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the creditService proxy RestClient.
 * The X-Api-Key is injected as a default header so it is added server-side on every request
 * and is never exposed in browser responses or logs.
 */
@Configuration
public class CreditProxyConfig {

    @Value("${creditservice.base-url}")
    private String baseUrl;

    @Value("${creditservice.api-key}")
    private String apiKey;

    @Bean("creditServiceRestClient")
    public RestClient creditServiceRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
