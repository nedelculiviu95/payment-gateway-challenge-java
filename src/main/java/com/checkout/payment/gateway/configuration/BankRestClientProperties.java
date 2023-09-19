package com.checkout.payment.gateway.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Holds and validates the bank address and connection timeouts
 */
@ConfigurationProperties(prefix = "bank")
public record BankRestClientProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {

    public BankRestClientProperties {
        validateBaseUrl(baseUrl);
        validateScheme(baseUrl.getScheme());
        validateHost(baseUrl.getHost());
        validateUserInfo(baseUrl.getUserInfo());
        validatePort(baseUrl.getPort());
        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
    }

    private static void validateBaseUrl(URI baseUrl) {
        if (baseUrl == null) {
            throw new IllegalArgumentException("Bank base URL must be provided");
        }
    }

    private static void validateScheme(String scheme) {
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Bank base URL must use HTTP or HTTPS");
        }
    }

    private static void validateHost(String host) {
        if (host == null) {
            throw new IllegalArgumentException("Bank base URL must include a host");
        }
    }

    private static void validateUserInfo(String userInfo) {
        if (userInfo != null) {
            throw new IllegalArgumentException("Bank base URL must not include credentials");
        }
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout == null
                || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {

            throw new IllegalArgumentException("Bank timeouts must be between 1ms and Integer.MAX_VALUE ms");
        }
    }

    // 65535 because TCP port numbers use an unsigned 16-bit value: 2¹⁶ − 1 = 65535
    private static void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Bank port must be omitted or between 1 and 65535");
        }
    }

    // Adds /payments to the base path, with or without a trailing slash, while keeping the host and port.
    public URI paymentsUrl() {
        return UriComponentsBuilder.fromUri(baseUrl).pathSegment("payments").build(true).toUri();
    }
}
