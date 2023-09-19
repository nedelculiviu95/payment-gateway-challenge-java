package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.exception.InvalidPaymentRequestException;
import com.checkout.payment.gateway.model.ErrorResponse.FieldError;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentRequestValidatorTest {

    private final PaymentRequestValidator validator = new PaymentRequestValidator(
            Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void acceptsValidRequestWithLeadingZeroes() {
        assertDoesNotThrow(() -> validator.validate(validRequest()));
    }

    @Test
    void rejectsNullRequest() {
        var exception = assertThrows(InvalidPaymentRequestException.class, () -> validator.validate(null));

        assertEquals(List.of(new FieldError("request", "REQUIRED", "A payment request is required")),
                exception.getErrors());
    }

    @Test
    void reportsAllMissingFieldsWithoutTryingToBuildAnExpiryDate() {
        var exception = assertThrows(InvalidPaymentRequestException.class,
                () -> validator.validate(new PostPaymentRequest()));

        assertEquals(List.of("card_number", "cvv", "expiry_month", "expiry_year", "currency", "amount"),
                exception.getErrors().stream().map(FieldError::field).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901234", "1234567890123456789"})
    void acceptsCardNumberLengthBoundaries(String cardNumber) {
        var request = validRequest();
        request.setCardNumber(cardNumber);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1234567890123", "12345678901234567890", "1234567890123a",
            "+12345678901234", "12345678901234 ", "12345678901234\n", "１２３４５６７８９０１２３４", "١٢٣٤٥٦٧٨٩٠١٢٣٤"})
    void rejectsMissingOrInvalidCardNumber(String cardNumber) {
        var request = validRequest();
        request.setCardNumber(cardNumber);

        assertSingleError(request, "card_number", "INVALID_FORMAT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"012", "0000"})
    void acceptsThreeOrFourDigitCvvWithLeadingZeroes(String cvv) {
        var request = validRequest();
        request.setCvv(cvv);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "12", "12345", "12a", "-12", "123 ", "123\n", "１２３", "١٢٣"})
    void rejectsMissingOrInvalidCvv(String cvv) {
        var request = validRequest();
        request.setCvv(cvv);

        assertSingleError(request, "cvv", "INVALID_FORMAT");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 13, Integer.MAX_VALUE})
    void rejectsMissingOrOutOfRangeExpiryMonth(Integer month) {
        var request = validRequest();
        request.setExpiryMonth(month);

        assertSingleError(request, "expiry_month", "INVALID_VALUE");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 12})
    void acceptsExpiryMonthBoundaries(int month) {
        var request = validRequest();
        request.setExpiryMonth(month);
        request.setExpiryYear(2027);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 10000, Integer.MAX_VALUE})
    void rejectsMissingOrOutOfRangeExpiryYear(Integer year) {
        var request = validRequest();
        request.setExpiryYear(year);

        assertSingleError(request, "expiry_year", "INVALID_VALUE");
    }

    @Test
    void acceptsLargestSupportedExpiryYear() {
        var request = validRequest();
        request.setExpiryYear(9999);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "12, 2025", "10, 2026", "11, 2026"})
    void rejectsPastAndCurrentExpiryMonths(int month, int year) {
        var request = validRequest();
        request.setExpiryMonth(month);
        request.setExpiryYear(year);

        assertSingleError(request, "expiry_date", "EXPIRED");
    }

    @Test
    void acceptsNextMonth() {
        var request = validRequest();
        request.setExpiryMonth(12);
        request.setExpiryYear(2026);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void acceptsJanuaryOfNextYearWhenCurrentMonthIsDecember() {
        var decemberValidator = new PaymentRequestValidator(
                Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC));
        var request = validRequest();
        request.setExpiryMonth(1);
        request.setExpiryYear(2027);

        assertDoesNotThrow(() -> decemberValidator.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GBP", "USD", "EUR"})
    void acceptsSupportedCurrencies(String currency) {
        var request = validRequest();
        request.setCurrency(currency);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"JPY", "gbp", " GBP", "GBP ", "GB", "GBPP", "123"})
    void rejectsMissingOrUnsupportedCurrency(String currency) {
        var request = validRequest();
        request.setCurrency(currency);

        assertSingleError(request, "currency", "UNSUPPORTED_CURRENCY");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void acceptsPositiveAmountBoundaries(int amount) {
        var request = validRequest();
        request.setAmount(amount);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    void rejectsMissingOrNonPositiveAmount(Integer amount) {
        var request = validRequest();
        request.setAmount(amount);

        assertSingleError(request, "amount", "INVALID_VALUE");
    }

    @Test
    void reportsSafeErrorsWithoutIncludingSubmittedCardOrCvv() {
        var request = validRequest();
        request.setCardNumber("invalid-card-value");
        request.setCvv("invalid-cvv-value");

        var exception = assertThrows(InvalidPaymentRequestException.class, () -> validator.validate(request));

        assertEquals(2, exception.getErrors().size());
        assertTrue(exception.getErrors().stream().noneMatch(error ->
                error.message().contains(request.getCardNumber()) || error.message().contains(request.getCvv())));
    }

    private void assertSingleError(PostPaymentRequest request, String field, String code) {
        var exception = assertThrows(InvalidPaymentRequestException.class, () -> validator.validate(request));

        assertEquals(1, exception.getErrors().size());
        assertEquals(field, exception.getErrors().get(0).field());
        assertEquals(code, exception.getErrors().get(0).code());
    }

    private PostPaymentRequest validRequest() {
        var request = new PostPaymentRequest();
        request.setCardNumber("0000000000000007");
        request.setCvv("012");
        request.setExpiryMonth(12);
        request.setExpiryYear(2027);
        request.setCurrency("GBP");
        request.setAmount(1050);
        return request;
    }
}
