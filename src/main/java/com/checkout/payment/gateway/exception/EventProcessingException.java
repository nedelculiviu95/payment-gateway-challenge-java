package com.checkout.payment.gateway.exception;

/** Signals a failure while processing a payment. */
public class EventProcessingException extends RuntimeException{
    public EventProcessingException(String message) {
        super(message);
    }
}
