package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.Locale;
import java.util.UUID;

/**
 * Carries saved payment details returned when a payment is retrieved
 */
public class GetPaymentResponse {
    private UUID id;
    private PaymentStatus status;
    private String cardNumberLastFour;
    private int expiryMonth;
    private int expiryYear;
    private String currency;
    private int amount;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getCardNumberLastFour() {
        return cardNumberLastFour;
    }

    public void setCardNumberLastFour(String cardNumberLastFour) {
        this.cardNumberLastFour = cardNumberLastFour;
    }

    public void setCardNumberLastFour(int cardNumberLastFour) {
        if (cardNumberLastFour < 0 || cardNumberLastFour > 9999) {
            throw new IllegalArgumentException("Last four card digits must be between 0 and 9999");
        }

        this.cardNumberLastFour = String.format(Locale.ROOT, "%04d", cardNumberLastFour);
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public void setExpiryMonth(int expiryMonth) {
        this.expiryMonth = expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public void setExpiryYear(int expiryYear) {
        this.expiryYear = expiryYear;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public static GetPaymentResponse from(Payment payment) {
        GetPaymentResponse response = new GetPaymentResponse();
        response.setId(payment.id());
        response.setStatus(payment.status());
        response.setCardNumberLastFour(payment.cardNumberLastFour());
        response.setExpiryMonth(payment.expiryMonth());
        response.setExpiryYear(payment.expiryYear());
        response.setCurrency(payment.currency());
        response.setAmount(payment.amount());
        return response;
    }

    @Override
    public String toString() {
        return "GetPaymentResponse{" +
                "id=" + id +
                ", status=" + status +
                ", cardNumberLastFour=" + cardNumberLastFour +
                ", expiryMonth=" + expiryMonth +
                ", expiryYear=" + expiryYear +
                ", currency='" + currency + '\'' +
                ", amount=" + amount +
                '}';
    }
}
