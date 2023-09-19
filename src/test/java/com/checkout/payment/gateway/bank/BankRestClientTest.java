package com.checkout.payment.gateway.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.configuration.BankRestClientProperties;
import com.checkout.payment.gateway.exception.BankRestClientException;
import com.checkout.payment.gateway.exception.BankRestClientException.Reason;
import com.checkout.payment.gateway.exception.CommonExceptionHandler;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class BankRestClientTest {

    private static final URI PAYMENTS_URL = URI.create("http://localhost:8080/payments");
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final BankRestClient client = new BankRestClient(restTemplate,
            new BankRestClientProperties(URI.create("http://localhost:8080"),
                    Duration.ofSeconds(2), Duration.ofSeconds(5)));
    private final BankPaymentRequest payment = new BankPaymentRequest("0000000000000007", "01/2030", "GBP", 1050, "012");

    @Test
    void sendsJsonPaymentAndReturnsAuthorizedResponse() {
        var bankResponse = new BankPaymentResponse(true, "bank-reference");
        stubResponse(ResponseEntity.ok(bankResponse));

        assertSame(bankResponse, client.sendPayment(payment));

        var request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(PAYMENTS_URL), request.capture(), eq(BankPaymentResponse.class));
        assertSame(payment, request.getValue().getBody());
        assertEquals(MediaType.APPLICATION_JSON, request.getValue().getHeaders().getContentType());
        assertEquals(List.of(MediaType.APPLICATION_JSON), request.getValue().getHeaders().getAccept());
        verifyNoMoreInteractions(restTemplate);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "optional-reference"})
    void acceptsDeclinedResponseWithoutRequiringAuthorizationCode(String code) {
        var bankResponse = new BankPaymentResponse(false, code);
        stubResponse(ResponseEntity.ok(bankResponse));

        assertSame(bankResponse, client.sendPayment(payment));
    }

    @Test
    void rejectsMissingHttpResponse() {
        stubResponse(null);

        assertFailure(Reason.INVALID_RESPONSE);
    }

    @Test
    void rejectsEmptyResponseBody() {
        stubResponse(ResponseEntity.ok().build());

        assertFailure(Reason.INVALID_RESPONSE);
    }

    @Test
    void rejectsMissingAuthorizationDecision() {
        stubResponse(ResponseEntity.ok(new BankPaymentResponse(null, "bank-reference")));

        assertFailure(Reason.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsAuthorizedResponseWithoutNonblankCode(String code) {
        stubResponse(ResponseEntity.ok(new BankPaymentResponse(true, code)));

        assertFailure(Reason.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 204, 301, 302, 400})
    void rejectsUnexpectedHttpStatus(int status) {
        stubResponse(ResponseEntity.status(status).body(new BankPaymentResponse(true, "bank-reference")));

        assertFailure(Reason.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void treatsReturnedServerErrorAsUnavailable(int status) {
        stubResponse(ResponseEntity.status(status).build());

        assertFailure(Reason.UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void mapsServerErrorExceptionToUnavailable(int status) {
        var failure = new HttpServerErrorException(HttpStatus.valueOf(status), "private-bank-message");
        stubFailure(failure);

        assertSame(failure, assertFailure(Reason.UNAVAILABLE).getCause());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429})
    void mapsClientErrorExceptionToInvalidResponse(int status) {
        var failure = new HttpClientErrorException(HttpStatus.valueOf(status), "private-bank-message");
        stubFailure(failure);

        assertSame(failure, assertFailure(Reason.INVALID_RESPONSE).getCause());
    }

    @Test
    void mapsJsonConversionFailureToInvalidResponse() {
        var failure = new RestClientException("private-bank-json");
        stubFailure(failure);

        assertSame(failure, assertFailure(Reason.INVALID_RESPONSE).getCause());
    }

    @Test
    void mapsInvalidMediaTypeToInvalidResponse() {
        var failure = new InvalidMediaTypeException("private-content-type", "Invalid header");
        stubFailure(failure);

        assertSame(failure, assertFailure(Reason.INVALID_RESPONSE).getCause());
    }

    @Test
    void preservesRequestConversionFailureAsInternalError() {
        var failure = new HttpMessageNotWritableException("private-request-diagnostic");
        stubFailure(failure);

        var exception = assertThrows(IllegalStateException.class, () -> client.sendPayment(payment));
        assertSame(failure, exception.getCause());
        assertEquals("Failed to serialize bank authorization request", exception.getMessage());
        verify(restTemplate).postForEntity(eq(PAYMENTS_URL), any(HttpEntity.class), eq(BankPaymentResponse.class));
        verifyNoMoreInteractions(restTemplate);
    }

    @Test
    void preservesTimeoutCauseWithoutExposingItsMessage() {
        var failure = new ResourceAccessException("private-timeout", new SocketTimeoutException("private-cause"));
        stubFailure(failure);

        assertSame(failure, assertFailure(Reason.TIMEOUT).getCause());
    }

    @Test
    void recognizesTimeoutNestedInCauseChain() {
        var failure = new ResourceAccessException("private-timeout", new IOException("private-wrapper", new SocketTimeoutException("private-cause")));
        stubFailure(failure);

        assertSame(failure, assertFailure(Reason.TIMEOUT).getCause());
    }

    @Test
    void preservesConnectionFailureCauseWithoutExposingItsMessage() {
        var failure = new ResourceAccessException("private-connection", new IOException("private-cause"));
        stubFailure(failure);

        assertSame(failure, assertFailure(Reason.UNAVAILABLE).getCause());
    }

    private void stubResponse(ResponseEntity<BankPaymentResponse> response) {
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(HttpEntity.class), eq(BankPaymentResponse.class))).thenReturn(response);
    }

    private void stubFailure(RuntimeException failure) {
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(HttpEntity.class), eq(BankPaymentResponse.class))).thenThrow(failure);
    }

    private BankRestClientException assertFailure(Reason reason) {
        var exception = assertThrows(BankRestClientException.class, () -> client.sendPayment(payment));
        assertEquals(reason, exception.getReason());

        String expectedMessage = switch (reason) {
            case TIMEOUT -> "The bank request timed out; the payment outcome is unknown";
            case UNAVAILABLE -> "The bank is unavailable; the payment outcome may be unknown";
            case INVALID_RESPONSE -> "The bank returned an invalid response; the payment outcome may be unknown";
        };
        assertEquals(expectedMessage, exception.getMessage());

        var body = new CommonExceptionHandler().handleBankFailure(exception).getBody();
        assertNotNull(body);
        assertEquals(expectedMessage, body.getMessage());
        verify(restTemplate).postForEntity(eq(PAYMENTS_URL), any(HttpEntity.class), eq(BankPaymentResponse.class));
        verifyNoMoreInteractions(restTemplate);

        return exception;
    }
}
