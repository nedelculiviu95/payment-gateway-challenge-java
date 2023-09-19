package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts application failures into HTTP error responses
 */
@ControllerAdvice
public class CommonExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, new ErrorResponse("PAYMENT_NOT_FOUND", "Payment not found"));
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPayment(InvalidPaymentRequestException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST, new ErrorResponse("PAYMENT_REJECTED", "The payment request is invalid", PaymentStatus.REJECTED, ex.getErrors()));
    }

    @ExceptionHandler(BankRestClientException.class)
    public ResponseEntity<ErrorResponse> handleBankFailure(BankRestClientException ex) {
        return switch (ex.getReason()) {
            case UNAVAILABLE -> bankErrorResponse(ex, HttpStatus.SERVICE_UNAVAILABLE, "BANK_UNAVAILABLE");
            case TIMEOUT -> bankErrorResponse(ex, HttpStatus.GATEWAY_TIMEOUT, "BANK_TIMEOUT");
            case INVALID_RESPONSE -> bankErrorResponse(ex, HttpStatus.BAD_GATEWAY, "BANK_RESPONSE_INVALID");
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedFailure(Exception ex) {
        LOG.error("Payment request failed: {}", ex.getClass().getSimpleName());
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        return handleExceptionInternal(ex,
                new ErrorResponse("PAYMENT_REJECTED", "The payment request JSON is invalid",
                        PaymentStatus.REJECTED, List.of()), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex,
                UUID.class.equals(ex.getRequiredType())
                        ? new ErrorResponse("INVALID_PAYMENT_ID", "Payment ID must be a valid UUID")
                        : new ErrorResponse("INVALID_REQUEST", "The request contains an invalid value"),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        if (status.is5xxServerError()) {
            LOG.error("HTTP request failed with status {}; exception={}", status.value(), ex.getClass().getSimpleName());
        }

        if (!(body instanceof ErrorResponse)) {
            HttpStatus knownStatus = HttpStatus.resolve(status.value());
            String message = knownStatus == null ? "The request could not be processed" : knownStatus.getReasonPhrase();
            body = new ErrorResponse("HTTP_ERROR", message);
        }

        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return errorResponse(status, headers, body);
    }

    private ResponseEntity<ErrorResponse> bankErrorResponse(BankRestClientException ex, HttpStatus status, String code) {
        LOG.warn("Bank authorization failed: {}. Got {} http status", code, status.value());
        return errorResponse(status, new ErrorResponse(code, ex.getMessage()));
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatusCode status, ErrorResponse body) {
        return errorResponse(status, HttpHeaders.EMPTY, body);
    }

    private <T> ResponseEntity<T> errorResponse(HttpStatusCode status, HttpHeaders headers, T body) {
        return ResponseEntity.status(status).headers(headers).cacheControl(CacheControl.noStore()).body(body);
    }

}
