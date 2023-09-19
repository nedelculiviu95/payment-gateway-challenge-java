package com.checkout.payment.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PaymentGatewayControllerTest {

    private final PaymentGatewayService service = mock(PaymentGatewayService.class);
    private final PaymentGatewayController controller = new PaymentGatewayController(service);

    @Test
    void createsPaymentWithCreatedStatusLocationAndServiceResponse() {
        UUID id = UUID.randomUUID();
        var request = new PostPaymentRequest();
        var serviceResponse = new PostPaymentResponse();
        serviceResponse.setId(id);
        when(service.processPayment(request)).thenReturn(serviceResponse);

        var response = controller.postPaymentEvent(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(URI.create("/payment/" + id), response.getHeaders().getLocation());
        assertSame(serviceResponse, response.getBody());

        verify(service).processPayment(request);
    }

    @Test
    void retrievesPaymentWithOkStatusAndServiceResponse() {
        UUID id = UUID.randomUUID();
        var serviceResponse = new GetPaymentResponse();
        serviceResponse.setId(id);
        when(service.getPaymentById(id)).thenReturn(serviceResponse);

        var response = controller.getPostPaymentEventById(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(serviceResponse, response.getBody());

        verify(service).getPaymentById(id);
    }
}
