package com.checkout.payment.gateway.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"AUTHORIZED", "DECLINED"})
    void acceptsConfirmedBankDecisions(PaymentStatus status) {
        var payment = assertDoesNotThrow(() -> new Payment(ID, status, "0007", 12, 2027, "GBP", 1050));

        assertEquals(status, payment.status());
    }

    @Test
    void rejectsRejectedStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(ID, PaymentStatus.REJECTED, "0007", 12, 2027, "GBP", 1050));
    }

    @Test
    void requiresPaymentId() {
        assertThrows(NullPointerException.class,
                () -> new Payment(null, PaymentStatus.AUTHORIZED, "0007", 12, 2027, "GBP", 1050));
    }

    @Test
    void requiresPaymentStatus() {
        assertThrows(NullPointerException.class,
                () -> new Payment(ID, null, "0007", 12, 2027, "GBP", 1050));
    }

    @Test
    void requiresCurrency() {
        assertThrows(NullPointerException.class,
                () -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", 12, 2027, null, 1050));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000", "0007", "9999"})
    void preservesExactlyFourCardDigitsIncludingLeadingZeroes(String lastFour) {
        var payment = new Payment(ID, PaymentStatus.AUTHORIZED, lastFour, 12, 2027, "GBP", 1050);

        assertEquals(lastFour, payment.cardNumberLastFour());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "12345", "12345678901234", "12a4", "-123", "123 ", "1234\n", "１２３４", "١٢٣٤"})
    void rejectsMissingOrInvalidLastFourDigits(String lastFour) {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(ID, PaymentStatus.AUTHORIZED, lastFour, 12, 2027, "GBP", 1050));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 13, Integer.MAX_VALUE})
    void rejectsOutOfRangeExpiryMonth(int month) {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", month, 2027, "GBP", 1050));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 10000, Integer.MAX_VALUE})
    void rejectsOutOfRangeExpiryYear(int year) {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", 12, year, "GBP", 1050));
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "12, 9999"})
    void acceptsExpiryComponentBoundariesForStoredPayments(int month, int year) {
        assertDoesNotThrow(() -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", month, year, "GBP", 1050));
    }

    @Test
    void acceptsExpiredCardForHistoricalPayment() {
        assertDoesNotThrow(() -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", 1, 2020, "GBP", 1050));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GBP", "USD", "EUR"})
    void acceptsSupportedCurrencies(String currency) {
        assertDoesNotThrow(() -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", 12, 2027, currency, 1050));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "JPY", "gbp", " GBP", "GBP ", "GB", "GBPP", "123"})
    void rejectsUnsupportedCurrency(String currency) {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", 12, 2027, currency, 1050));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void preservesPositiveAmountBoundaries(int amount) {
        var payment = new Payment(ID, PaymentStatus.AUTHORIZED, "0007", 12, 2027, "GBP", amount);

        assertEquals(amount, payment.amount());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    void rejectsNonPositiveAmount(int amount) {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(ID, PaymentStatus.AUTHORIZED, "0007", 12, 2027, "GBP", amount));
    }
}
