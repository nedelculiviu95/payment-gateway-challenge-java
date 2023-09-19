package com.checkout.payment.gateway.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.WebUtils;

class CommonExceptionHandlerTest {

    private final CommonExceptionHandler handler = new CommonExceptionHandler();

    @ParameterizedTest
    @MethodSource("frameworkFailures")
    void doesNotWriteToCommittedResponses(Exception exception) throws Exception {
        var response = new MockHttpServletResponse();
        response.flushBuffer();

        assertNull(handler.handleException(exception, new ServletWebRequest(new MockHttpServletRequest(), response)));
        assertTrue(response.isCommitted());
        assertEquals("", response.getContentAsString());
    }

    @Test
    void preservesFrameworkServerErrorTrackingAndUsesSafeBody() throws Exception {
        var request = new MockHttpServletRequest();
        var exception = new HttpMessageNotWritableException("private-diagnostic");

        var response = handler.handleException(exception, new ServletWebRequest(request, new MockHttpServletResponse()));

        assertSame(exception, request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("HTTP_ERROR", body.getCode());
        assertEquals("Internal Server Error", body.getMessage());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void preservesAllowHeaderOnMethodNotAllowed() throws Exception {
        var exception = new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST"));

        var response = handler.handleException(exception,
                new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("GET, POST", response.getHeaders().getFirst(HttpHeaders.ALLOW));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void preservesSpecificJsonAndUuidErrors() throws Exception {
        var request = new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());
        var jsonResponse = handler.handleException(new HttpMessageNotReadableException("private-diagnostic", new MockHttpInputMessage(new byte[0])), request);
        var uuidResponse = handler.handleException(new TypeMismatchException("invalid", UUID.class), request);

        assertEquals(HttpStatus.BAD_REQUEST, jsonResponse.getStatusCode());

        var jsonBody = assertInstanceOf(ErrorResponse.class, jsonResponse.getBody());
        assertEquals("PAYMENT_REJECTED", jsonBody.getCode());
        assertEquals(PaymentStatus.REJECTED, jsonBody.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, uuidResponse.getStatusCode());

        var uuidBody = assertInstanceOf(ErrorResponse.class, uuidResponse.getBody());
        assertEquals("INVALID_PAYMENT_ID", uuidBody.getCode());
    }

    @Test
    void mapsOnlyMissingPaymentsToNotFound() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController()).setControllerAdvice(handler).build();

        mvc.perform(get("/missing-payment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Payment not found"));

        mvc.perform(get("/processing-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({"UNAVAILABLE,503,BANK_UNAVAILABLE", "TIMEOUT,504,BANK_TIMEOUT",
            "INVALID_RESPONSE,502,BANK_RESPONSE_INVALID"})
    void mapsBankFailuresToHttpErrors(BankRestClientException.Reason reason,
            int expectedStatus, String expectedCode) {
        var exception = new BankRestClientException(reason);

        var response = handler.handleBankFailure(exception);

        assertEquals(expectedStatus, response.getStatusCode().value());
        assertEquals(expectedCode, response.getBody().getCode());
        assertEquals(exception.getMessage(), response.getBody().getMessage());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void returnsSafeInternalErrorForUnexpectedFailure() {
        var exception = new IllegalStateException("private-root-message");

        var response = handler.handleUnexpectedFailure(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getCode());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }

    @RestController
    static class FailureController {
        @GetMapping("/missing-payment")
        void missingPayment() {
            throw new PaymentNotFoundException();
        }

        @GetMapping("/processing-failure")
        void processingFailure() {
            throw new EventProcessingException("private-diagnostic");
        }
    }

    private static Stream<Exception> frameworkFailures() {
        return Stream.of(
                new HttpMessageNotWritableException("private-diagnostic"),
                new HttpMessageNotReadableException("private-diagnostic", new MockHttpInputMessage(new byte[0])),
                new TypeMismatchException("private-diagnostic", UUID.class));
    }
}
