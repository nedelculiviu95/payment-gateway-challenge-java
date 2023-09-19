package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.exception.CommonExceptionHandler;
import com.checkout.payment.gateway.exception.PaymentAlreadyExistsException;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PostPaymentResponse;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Stores payment results in memory so they can be retrieved by ID
 */
@Repository
public class PaymentsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentsRepository.class);

    private final HashMap<UUID, Payment> payments = new HashMap<>();

    public void add(Payment payment) {
        Objects.requireNonNull(payment, "Payment is required");

        LOG.debug("Saved payment id={}", payment.id());

        if (payments.putIfAbsent(payment.id(), payment) != null) {
            throw new PaymentAlreadyExistsException();
        }
    }

    public Optional<Payment> get(UUID id) {
        LOG.debug("Looking up payment id={}", id);

        return Optional.ofNullable(payments.get(id));
    }
}
