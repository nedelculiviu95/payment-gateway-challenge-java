package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Carries error details returned to the API caller
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {

    private final String code;
    private final String message;
    private final PaymentStatus status;
    private final List<FieldError> errors;

    public ErrorResponse(String message) {
        this(null, message, null, List.of());
    }

    public ErrorResponse(String code, String message) {
        this(code, message, null, List.of());
    }

    public ErrorResponse(String code, String message, PaymentStatus status, List<FieldError> errors) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    // Describes a validation problem with one request field
    public record FieldError(String field, String code, String message) {
    }
}
