package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.bank.BankRestClient;
import com.checkout.payment.gateway.bank.BankPaymentRequest;
import com.checkout.payment.gateway.bank.BankPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Coordinates payment validation, bank authorization, storage, and retrieval
 */
@Service
public class PaymentGatewayService {

    private final PaymentsRepository paymentsRepository;
    private final PaymentRequestValidator paymentRequestValidator;
    private final BankRestClient bankRestClient;

    public PaymentGatewayService(PaymentsRepository paymentsRepository, PaymentRequestValidator paymentRequestValidator, BankRestClient bankRestClient) {
        this.paymentsRepository = paymentsRepository;
        this.paymentRequestValidator = paymentRequestValidator;
        this.bankRestClient = bankRestClient;
    }

    public GetPaymentResponse getPaymentById(UUID id) {
        Payment payment = paymentsRepository.get(id).orElseThrow(PaymentNotFoundException::new);
        return GetPaymentResponse.from(payment);
    }

    public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
        paymentRequestValidator.validate(paymentRequest);

        BankPaymentResponse bankResponse = bankRestClient.sendPayment(BankPaymentRequest.from(paymentRequest));

        PaymentStatus paymentStatus = bankResponse.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
        String cardNumber = paymentRequest.getCardNumber();
        String cardNumberLastFour = paymentRequest.getCardNumber().substring(cardNumber.length() - 4);

        Payment payment = new Payment(UUID.randomUUID(),
                paymentStatus,
                cardNumberLastFour,
                paymentRequest.getExpiryMonth(),
                paymentRequest.getExpiryYear(),
                paymentRequest.getCurrency(),
                paymentRequest.getAmount());

        paymentsRepository.add(payment);

        return PostPaymentResponse.from(payment);
    }
}
