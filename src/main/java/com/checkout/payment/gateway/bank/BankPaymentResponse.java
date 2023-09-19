package com.checkout.payment.gateway.bank;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Carries the bank's authorization decision and reference code
 */
public record BankPaymentResponse(
        Boolean authorized,
        @JsonProperty("authorization_code") String authorizationCode) {
}
