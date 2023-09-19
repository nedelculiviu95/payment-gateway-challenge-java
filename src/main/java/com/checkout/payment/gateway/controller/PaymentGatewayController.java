package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes API endpoints for creating and retrieving payments
 */
@RestController("api")
public class PaymentGatewayController {

    private final PaymentGatewayService paymentGatewayService;

    public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    @GetMapping("/payment/{id}")
    public ResponseEntity<GetPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
        return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
    }

    @PostMapping(value = "/payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostPaymentResponse> postPaymentEvent(@RequestBody PostPaymentRequest request) {
        PostPaymentResponse response = paymentGatewayService.processPayment(request);

        return ResponseEntity.created(URI.create("/payment/" + response.getId())).body(response);
    }
}
