package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.exception.BankRestClientException;
import com.checkout.payment.gateway.exception.BankRestClientException.Reason;
import com.checkout.payment.gateway.configuration.BankRestClientProperties;
import java.net.SocketTimeoutException;
import java.util.List;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Sends payment requests to the bank and checks its responses
 */

@Component
public class BankRestClient {

    private final RestTemplate restTemplate;
    private final BankRestClientProperties properties;

    public BankRestClient(RestTemplate restTemplate, BankRestClientProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public BankPaymentResponse sendPayment(BankPaymentRequest paymentRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            var response = restTemplate.postForEntity(properties.paymentsUrl(), new HttpEntity<>(paymentRequest, headers), BankPaymentResponse.class);

            if (response == null) {
                throw new BankRestClientException(Reason.INVALID_RESPONSE);
            }

            if (response.getStatusCode().value() != 200) {
                throw new BankRestClientException(response.getStatusCode().is5xxServerError() ? Reason.UNAVAILABLE : Reason.INVALID_RESPONSE);
            }

            BankPaymentResponse paymentResponse = response.getBody();

            validateResponse(paymentResponse);

            return paymentResponse;
        } catch (ResourceAccessException e) { // Connection, DNS, TLS, or I/O failures during the cal
            throw new BankRestClientException(isTimeoutException(e) ? Reason.TIMEOUT : Reason.UNAVAILABLE, e);

        } catch (HttpServerErrorException e) { // The bank returns HTTP 5xx, with default error handling enabled
            throw new BankRestClientException(Reason.UNAVAILABLE, e);

        } catch (HttpMessageNotWritableException e) { // Spring cannot serialize our outgoing payment request
            throw new IllegalStateException("Failed to serialize bank authorization request", e);

        } catch (RestClientException | InvalidMediaTypeException e) {
            // InvalidMediaTypeException - The bank supplies a malformed Content-Type header
            // RestClientException - Other client failures, including HTTP 4xx, unsupported response content types, and wrapped JSON-reading failures
            throw new BankRestClientException(Reason.INVALID_RESPONSE, e);
        }
    }

    private void validateResponse(BankPaymentResponse response) {
        // Separated the if statements to make the checks clear and easier to read
        if (response == null || response.authorized() == null) {
            throw new BankRestClientException(Reason.INVALID_RESPONSE);
        }

        if (response.authorized() && (response.authorizationCode() == null || response.authorizationCode().isBlank())) {
            throw new BankRestClientException(Reason.INVALID_RESPONSE);
        }
    }

    private boolean isTimeoutException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }
}
