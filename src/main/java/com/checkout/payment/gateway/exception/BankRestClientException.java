package com.checkout.payment.gateway.exception;

/**
 * Reports a bank communication or response failure using a safe message
 */
public class BankRestClientException extends RuntimeException {

    /** Identifies why the bank call failed. */
    public enum Reason {
        UNAVAILABLE, TIMEOUT, INVALID_RESPONSE
    }

    private final Reason reason;

    public BankRestClientException(Reason reason) {
        this(reason, null);
    }

    public BankRestClientException(Reason reason, Throwable cause) {
        super(messageFor(reason), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    private static String messageFor(Reason reason) {
        return switch (reason) {
            case UNAVAILABLE -> "The bank is unavailable; the payment outcome may be unknown";
            case TIMEOUT -> "The bank request timed out; the payment outcome is unknown";
            case INVALID_RESPONSE -> "The bank returned an invalid response; the payment outcome may be unknown";
        };
    }
}
