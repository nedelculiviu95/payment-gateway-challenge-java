package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stores a payment internally, separately from the response objects sent to API clients.
 * It checks its values when created and cannot be changed afterwards, protecting stored payments.
 * This also lets API responses change without changing how payments are stored.
 */
public record Payment(
        UUID id,
        PaymentStatus status,
        String cardNumberLastFour,
        int expiryMonth,
        int expiryYear,
        String currency,
        int amount) {

    public Payment {
        Objects.requireNonNull(id, "Payment ID is required");
        Objects.requireNonNull(status, "Payment status is required");
        Objects.requireNonNull(currency, "Payment currency is required");

        if (status == PaymentStatus.REJECTED) {
            throw new IllegalArgumentException("A rejected request cannot be stored as a payment");
        }

        if (cardNumberLastFour == null || !cardNumberLastFour.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Last four card digits must contain four digits");
        }

        if (expiryMonth < 1 || expiryMonth > 12 || expiryYear < 1 || expiryYear > 9999) {
            throw new IllegalArgumentException("Payment expiry is invalid");
        }

        if (!Set.of("GBP", "USD", "EUR").contains(currency) || amount <= 0) {
            throw new IllegalArgumentException("Payment currency or amount is invalid");
        }
    }
}
