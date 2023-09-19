package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import java.io.Serializable;

/**
 * Carries the card and payment details submitted by a merchant
 */
public class PostPaymentRequest implements Serializable {

    @JsonProperty(value = "card_number", access = Access.WRITE_ONLY)
    private String cardNumber;
    @JsonProperty("expiry_month")
    private Integer expiryMonth;
    @JsonProperty("expiry_year")
    private Integer expiryYear;
    private String currency;
    private Integer amount;
    @JsonProperty(access = Access.WRITE_ONLY)
    private String cvv;

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public Integer getExpiryMonth() {
        return expiryMonth;
    }

    public void setExpiryMonth(Integer expiryMonth) {
        this.expiryMonth = expiryMonth;
    }

    public Integer getExpiryYear() {
        return expiryYear;
    }

    public void setExpiryYear(Integer expiryYear) {
        this.expiryYear = expiryYear;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getCvv() {
        return cvv;
    }

    public void setCvv(String cvv) {
        this.cvv = cvv;
    }

    @Override
    public String toString() {
        return "PostPaymentRequest{cardNumber=[REDACTED], cvv=[REDACTED]}";
    }


}
