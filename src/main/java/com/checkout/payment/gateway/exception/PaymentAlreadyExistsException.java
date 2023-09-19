package com.checkout.payment.gateway.exception;

/** Signals that a payment with the same ID is already stored. */
public class PaymentAlreadyExistsException extends IllegalStateException {

    public PaymentAlreadyExistsException() {
        super("A payment with this ID already exists");
    }
}
