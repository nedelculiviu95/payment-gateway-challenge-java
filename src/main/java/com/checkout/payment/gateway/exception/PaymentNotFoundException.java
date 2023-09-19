package com.checkout.payment.gateway.exception;

/** Signals that no payment was found for the requested ID. */
public class PaymentNotFoundException extends EventProcessingException {

    public PaymentNotFoundException() {
        super("Payment not found");
    }
}
