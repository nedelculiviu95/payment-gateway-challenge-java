package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.bank.BankPaymentRequest;
import com.checkout.payment.gateway.bank.BankPaymentResponse;
import com.checkout.payment.gateway.bank.BankRestClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankRestClientException;
import com.checkout.payment.gateway.exception.InvalidPaymentRequestException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class PaymentGatewayServiceTest {

    private final PaymentsRepository repository = mock(PaymentsRepository.class);
    private final BankRestClient bankClient = mock(BankRestClient.class);
    private final PaymentRequestValidator validator = new PaymentRequestValidator(Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));
    private final PaymentGatewayService service = new PaymentGatewayService(repository, validator, bankClient);

    @Test
    void rejectsNullRequestWithoutCallingBankOrRepository() {
        assertThrows(InvalidPaymentRequestException.class, () -> service.processPayment(null));

        verifyNoInteractions(bankClient, repository);
    }

    @Test
    void rejectsInvalidRequestWithoutCallingBankOrRepository() {
        var request = validRequest();
        request.setAmount(0);

        assertThrows(InvalidPaymentRequestException.class, () -> service.processPayment(request));

        verifyNoInteractions(bankClient, repository);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"AUTHORIZED", "DECLINED"})
    void savesBankDecisionAndReturnsMatchingPayment(PaymentStatus status) {
        var request = validRequest();
        boolean authorized = status == PaymentStatus.AUTHORIZED;
        when(bankClient.sendPayment(any())).thenReturn(new BankPaymentResponse(authorized, authorized ? "bank-reference" : ""));

        var response = service.processPayment(request);

        var savedPayment = ArgumentCaptor.forClass(Payment.class);
        var calls = inOrder(bankClient, repository);
        calls.verify(bankClient).sendPayment(new BankPaymentRequest("0000000000000007", "01/2027", "GBP", 1050, "012"));
        calls.verify(repository).add(savedPayment.capture());

        Payment stored = savedPayment.getValue();
        assertNotNull(stored.id());
        assertEquals(status, stored.status());
        assertEquals("0007", stored.cardNumberLastFour());
        assertEquals(1, stored.expiryMonth());
        assertEquals(2027, stored.expiryYear());
        assertEquals("GBP", stored.currency());
        assertEquals(1050, stored.amount());

        assertEquals(stored.id(), response.getId());
        assertEquals(stored.status(), response.getStatus());
        assertEquals(stored.cardNumberLastFour(), response.getCardNumberLastFour());
        assertEquals(stored.expiryMonth(), response.getExpiryMonth());
        assertEquals(stored.expiryYear(), response.getExpiryYear());
        assertEquals(stored.currency(), response.getCurrency());
        assertEquals(stored.amount(), response.getAmount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void preservesAmountBoundariesWhenCallingBankAndSavingPayment(int amount) {
        var request = validRequest();
        request.setAmount(amount);
        when(bankClient.sendPayment(any())).thenReturn(new BankPaymentResponse(true, "bank-reference"));

        var response = service.processPayment(request);
        var sentRequest = ArgumentCaptor.forClass(BankPaymentRequest.class);
        var savedPayment = ArgumentCaptor.forClass(Payment.class);

        verify(bankClient).sendPayment(sentRequest.capture());
        verify(repository).add(savedPayment.capture());

        assertEquals(amount, sentRequest.getValue().amount());
        assertEquals(amount, savedPayment.getValue().amount());
        assertEquals(amount, response.getAmount());
    }

    @ParameterizedTest
    @EnumSource(BankRestClientException.Reason.class)
    void propagatesBankFailureWithoutSavingPayment(BankRestClientException.Reason reason) {
        var failure = new BankRestClientException(reason);
        when(bankClient.sendPayment(any())).thenThrow(failure);

        var exception = assertThrows(BankRestClientException.class, () -> service.processPayment(validRequest()));

        assertSame(failure, exception);
        verify(bankClient).sendPayment(any());
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesUnexpectedBankFailureWithoutSavingPayment() {
        var failure = new IllegalStateException("Bank client failed");
        when(bankClient.sendPayment(any())).thenThrow(failure);

        var exception = assertThrows(IllegalStateException.class, () -> service.processPayment(validRequest()));

        assertSame(failure, exception);
        verify(bankClient).sendPayment(any());
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesRepositoryFailureAfterBankDecision() {
        var failure = new IllegalStateException("Storage failed");
        when(bankClient.sendPayment(any())).thenReturn(new BankPaymentResponse(true, "bank-reference"));
        doThrow(failure).when(repository).add(any());

        var exception = assertThrows(IllegalStateException.class, () -> service.processPayment(validRequest()));

        assertSame(failure, exception);
        verify(bankClient).sendPayment(any());
        verify(repository).add(any());
    }

    @Test
    void generatesDifferentGatewayIdsForSeparatePayments() {
        when(bankClient.sendPayment(any())).thenReturn(new BankPaymentResponse(true, "same-bank-reference"));

        var first = service.processPayment(validRequest());
        var second = service.processPayment(validRequest());

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertNotEquals(first.getId(), second.getId());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"AUTHORIZED", "DECLINED"})
    void retrievesHistoricalPaymentWithoutCallingBankOrRevalidatingExpiry(PaymentStatus status) {
        UUID id = UUID.randomUUID();
        var payment = new Payment(id, status, "0007", 1, 2020, "EUR", Integer.MAX_VALUE);
        when(repository.get(id)).thenReturn(Optional.of(payment));

        var response = service.getPaymentById(id);

        assertEquals(id, response.getId());
        assertEquals(status, response.getStatus());
        assertEquals("0007", response.getCardNumberLastFour());
        assertEquals(1, response.getExpiryMonth());
        assertEquals(2020, response.getExpiryYear());
        assertEquals("EUR", response.getCurrency());
        assertEquals(Integer.MAX_VALUE, response.getAmount());

        verify(repository).get(id);
        verifyNoInteractions(bankClient);
    }

    @Test
    void throwsPaymentNotFoundForUnknownIdWithoutCallingBank() {
        UUID id = UUID.randomUUID();
        when(repository.get(id)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> service.getPaymentById(id));

        verify(repository).get(id);
        verifyNoInteractions(bankClient);
    }

    private PostPaymentRequest validRequest() {
        var request = new PostPaymentRequest();
        request.setCardNumber("0000000000000007");
        request.setCvv("012");
        request.setExpiryMonth(1);
        request.setExpiryYear(2027);
        request.setCurrency("GBP");
        request.setAmount(1050);
        return request;
    }
}
