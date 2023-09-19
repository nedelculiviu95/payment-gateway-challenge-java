package com.checkout.payment.gateway.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BankRestClientPropertiesTest {

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8080,http://localhost:8080/payments",
            "http://localhost:8080/,http://localhost:8080/payments",
            "https://bank.example:443,https://bank.example:443/payments",
            "https://bank.example:443/api,https://bank.example:443/api/payments",
            "https://bank.example:443/api/,https://bank.example:443/api/payments",
            "https://bank.example:443/api%20v1/,https://bank.example:443/api%20v1/payments",
    })
    void appendsPaymentsPathWithOneSeparator(String baseUrl, String expectedUrl) {
        var properties = new BankRestClientProperties(
                URI.create(baseUrl), Duration.ofSeconds(2), Duration.ofSeconds(5));

        URI endpoint = properties.paymentsUrl();

        assertEquals(URI.create(expectedUrl), endpoint);
        assertEquals(properties.baseUrl().getHost(), endpoint.getHost());
        assertEquals(properties.baseUrl().getPort(), endpoint.getPort());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://bank.example:443", "https://bank.example:1", "https://bank.example:65535"})
    void acceptsValidExplicitPorts(String url) {
        assertDoesNotThrow(() -> new BankRestClientProperties(URI.create(url), Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://bank.example:0", "https://bank.example:65536"})
    void rejectsInvalidExplicitPorts(String url) {
        assertThrows(IllegalArgumentException.class, () -> new BankRestClientProperties(URI.create(url), Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Integer.MAX_VALUE})
    void acceptsTimeoutBoundaries(long milliseconds) {
        Duration timeout = Duration.ofMillis(milliseconds);
        assertDoesNotThrow(() -> new BankRestClientProperties(URI.create("https://bank.example:443"), timeout, timeout));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-PT0.001S", "PT0S", "PT0.0005S", "PT2147483.648S"})
    void rejectsTimeoutsOutsideSupportedRange(String value) {
        Duration timeout = Duration.parse(value);
        URI url = URI.create("https://bank.example:443");
        assertThrows(IllegalArgumentException.class, () -> new BankRestClientProperties(url, timeout, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new BankRestClientProperties(url, Duration.ofSeconds(2), timeout));
    }
}
