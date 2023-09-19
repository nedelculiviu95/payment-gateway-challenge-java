package com.checkout.payment.gateway.configuration;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Sets up shared objects and settings used by the payment gateway.
 */
@Configuration
@EnableConfigurationProperties(BankRestClientProperties.class)
public class ApplicationConfiguration {

    /** Logs requests to the console; defaults exclude bodies, headers, query strings and client details. */
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        return new CommonsRequestLoggingFilter();
    }

    /**
     * Provides the current time in UTC; tests can supply a fixed clock instead.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Creates the bank HTTP client with time limits and default HTTP error handling.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, BankRestClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));

        return builder.requestFactory(() -> requestFactory).build();
    }
}
