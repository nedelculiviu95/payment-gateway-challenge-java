package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.InvalidPaymentRequestException;
import com.checkout.payment.gateway.model.ErrorResponse.FieldError;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks incoming payment details before they are sent to the bank
 */
@Component
public class PaymentRequestValidator {

    private static final Pattern CARD_NUMBER = Pattern.compile("[0-9]{14,19}");
    private static final Pattern CVV = Pattern.compile("[0-9]{3,4}");
    private static final Set<String> CURRENCIES = Set.of("GBP", "USD", "EUR");

    // Inject Clock is necessary for reliable testing. A test can supply a fixed date
    // With the Clock.systemUTC() bean from Application configuration, the system uses the actual current time.
    private final Clock clock;

    public PaymentRequestValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(PostPaymentRequest request) {
        List<FieldError> errors = new ArrayList<>();
        if (request == null) {
            throw new InvalidPaymentRequestException(List.of(
                    new FieldError("request", "REQUIRED", "A payment request is required")));
        }

        if (request.getCardNumber() == null || !CARD_NUMBER.matcher(request.getCardNumber()).matches()) {
            errors.add(new FieldError("card_number", "INVALID_FORMAT",
                    "Card number must contain 14 to 19 digits"));
        }

        if (request.getCvv() == null || !CVV.matcher(request.getCvv()).matches()) {
            errors.add(new FieldError("cvv", "INVALID_FORMAT", "CVV must contain 3 or 4 digits"));
        }

        boolean validMonth = request.getExpiryMonth() != null && request.getExpiryMonth() >= 1 && request.getExpiryMonth() <= 12;
        boolean validYear = request.getExpiryYear() != null && request.getExpiryYear() >= 1 && request.getExpiryYear() <= 9999;

        if (!validMonth) {
            errors.add(new FieldError("expiry_month", "INVALID_VALUE", "Expiry month must be an integer from 1 to 12"));
        }

        if (!validYear) {
            errors.add(new FieldError("expiry_year", "INVALID_VALUE", "Expiry year must be an integer from 1 to 9999"));
        }

        if (validMonth && validYear
                && !YearMonth.of(request.getExpiryYear(), request.getExpiryMonth()).isAfter(YearMonth.now(clock))) {
            errors.add(new FieldError("expiry_date", "EXPIRED", "Expiry month and year must be in the future"));
        }

        if (request.getCurrency() == null || !CURRENCIES.contains(request.getCurrency())) {
            errors.add(new FieldError("currency", "UNSUPPORTED_CURRENCY", "Currency must be GBP, USD or EUR"));
        }

        if (request.getAmount() == null || request.getAmount() <= 0) {
            errors.add(new FieldError("amount", "INVALID_VALUE", "Amount must be a positive integer in minor currency units"));
        }

        if (!errors.isEmpty()) {
            throw new InvalidPaymentRequestException(errors);
        }
    }
}
