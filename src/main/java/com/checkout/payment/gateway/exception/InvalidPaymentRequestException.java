package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse.FieldError;
import java.util.List;

/** Reports payment fields that failed validation. */
public class InvalidPaymentRequestException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidPaymentRequestException(List<FieldError> errors) {
        super("The payment request is invalid");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
