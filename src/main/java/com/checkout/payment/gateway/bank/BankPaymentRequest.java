package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/**
 * Carries the card and payment details sent to the bank for authorization
 */
public record BankPaymentRequest(
        @JsonProperty("card_number") String cardNumber,
        @JsonProperty("expiry_date") String expiryDate,
        String currency,
        int amount,
        String cvv) {

    public static BankPaymentRequest from(PostPaymentRequest request) {
        // Locale.ROOT: keeps formatting independent of the machine’s default locale
        // %02d: month padded with zeros to at least two digits
        // %04d: year padded with zeros to at least four digits
        String expiryDate = String.format(Locale.ROOT, "%02d/%04d", request.getExpiryMonth(), request.getExpiryYear());

        return new BankPaymentRequest(request.getCardNumber(), expiryDate, request.getCurrency(), request.getAmount(), request.getCvv());
    }

    /**
     * This is needed because a record’s generated toString() includes its component values,
     * which would expose the full card number and CVV if logged
     */
    @Override
    public String toString() {
        return "BankPaymentRequest{" +
                ", cardNumber=[REDACTED]" +
                ", cvv=[REDACTED]" +
                ", expiryDate='" + expiryDate + '\'' +
                ", currency='" + currency + '\'' +
                ", amount=" + amount + "}";
    }
}
