package com.checkout.payment.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

/**
 * Critical set of integration tests against the bank simulator
 * Tests 3 scenarios:
 *  1. Authorized payment
 *  2. Declined payment
 *  3. Bank returns 503
 *
 * For these tests to work you need to have the bank simulator container running
 */

@Tag("bank-integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bank.base-url=http://localhost:8080")
class BankSimulatorIntegrationTest {

    private static final YearMonth NEXT_YEAR = YearMonth.now(ZoneOffset.UTC).plusYears(1);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void requireRunningSimulator() throws Exception {
        // Confirm the simulator is running so an absent bank cannot make the 503 test pass.
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:2525/imposters/8080")).timeout(Duration.ofSeconds(5)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Got 200 -> bank simulator is running
        assertEquals(200, response.statusCode());
    }

    @Test
    void createsAndRetrievesAuthorizedPayment() {
        assertPostAndGetPayment("2222405343248877", "Authorized");
    }

    @Test
    void createsAndRetrievesDeclinedPayment() {
        assertPostAndGetPayment("2222405343248878", "Declined");
    }

    @Test
    void reportsBankServiceUnavailableAsTechnicalFailure() {
        var paymentPostResponse = restTemplate.postForEntity("/payment", request("2222405343248870"), JsonNode.class);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, paymentPostResponse.getStatusCode());

        JsonNode body = paymentPostResponse.getBody();
        assertNotNull(body);
        assertEquals("BANK_UNAVAILABLE", body.path("code").asText());
        assertFalse(body.path("message").asText().isBlank());
        assertFalse(body.has("status"));
        assertFalse(body.has("id"));
        assertFalse(body.has("card_number"));
        assertFalse(body.has("cvv"));
    }

    private void assertPostAndGetPayment(String cardNumber, String expectedStatus) {
        var paymentPostResponse = restTemplate.postForEntity("/payment", request(cardNumber), JsonNode.class);
        assertEquals(HttpStatus.CREATED, paymentPostResponse.getStatusCode());

        JsonNode body = paymentPostResponse.getBody();
        assertNotNull(body);

        String id = body.path("id").asText();
        UUID payment = UUID.fromString(id);

        JsonNode expected = objectMapper.valueToTree(Map.of(
                "id", payment,
                "status", expectedStatus,
                "cardNumberLastFour", cardNumber.substring(cardNumber.length() - 4),
                "expiryMonth", NEXT_YEAR.getMonthValue(),
                "expiryYear", NEXT_YEAR.getYear(),
                "currency", "GBP",
                "amount", 1050));

        assertEquals(expected, body);
        assertEquals(URI.create("/payment/" + id), paymentPostResponse.getHeaders().getLocation());

        var paymentGetResponse = restTemplate.getForEntity(paymentPostResponse.getHeaders().getLocation().toString(), JsonNode.class);
        assertEquals(HttpStatus.OK, paymentGetResponse.getStatusCode());
        assertEquals(expected, paymentGetResponse.getBody());
    }

    private Map<String, Object> request(String cardNumber) {
        return Map.of(
                "card_number", cardNumber,
                "expiry_month", NEXT_YEAR.getMonthValue(),
                "expiry_year", NEXT_YEAR.getYear(),
                "currency", "GBP",
                "amount", 1050,
                "cvv", "012");
    }
}
