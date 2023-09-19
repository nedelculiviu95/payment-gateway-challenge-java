package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentAlreadyExistsException;
import com.checkout.payment.gateway.model.Payment;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

    private final PaymentsRepository repository = new PaymentsRepository();

    @Test
    void retrievesSavedPaymentById() {
        Payment payment = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);

        repository.add(payment);

        assertEquals(payment, repository.get(payment.id()).orElseThrow());
    }

    @Test
    void keepsPaymentsWithDifferentIdsSeparate() {
        Payment authorized = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);
        Payment declined = payment(UUID.randomUUID(), PaymentStatus.DECLINED);

        repository.add(authorized);
        repository.add(declined);

        assertEquals(authorized, repository.get(authorized.id()).orElseThrow());
        assertEquals(declined, repository.get(declined.id()).orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownId() {
        repository.add(payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED));

        assertTrue(repository.get(UUID.randomUUID()).isEmpty());
    }

    @Test
    void returnsEmptyForNullId() {
        assertTrue(repository.get(null).isEmpty());
    }

    @Test
    void rejectsNullPaymentWithoutChangingStoredPayments() {
        Payment existing = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);
        repository.add(existing);

        assertThrows(NullPointerException.class, () -> repository.add(null));

        assertEquals(existing, repository.get(existing.id()).orElseThrow());
    }

    @Test
    void rejectsAddingTheSamePaymentTwice() {
        Payment existing = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);
        repository.add(existing);

        assertThrows(PaymentAlreadyExistsException.class, () -> repository.add(existing));

        assertEquals(existing, repository.get(existing.id()).orElseThrow());
    }

    @Test
    void rejectsDuplicateIdWithoutReplacingOriginalPayment() {
        UUID id = UUID.randomUUID();
        Payment existing = payment(id, PaymentStatus.AUTHORIZED);
        Payment replacement = payment(id, PaymentStatus.DECLINED);
        repository.add(existing);

        assertThrows(PaymentAlreadyExistsException.class, () -> repository.add(replacement));

        assertEquals(existing, repository.get(id).orElseThrow());
    }

    @Test
    void keepsStorageSeparateBetweenRepositoryInstances() {
        Payment payment = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);
        repository.add(payment);

        assertTrue(new PaymentsRepository().get(payment.id()).isEmpty());
    }

    private Payment payment(UUID id, PaymentStatus status) {
        return new Payment(id, status, "0007", 12, 2027, "GBP", 1050);
    }
}
