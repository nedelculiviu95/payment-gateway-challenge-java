package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.bank.BankPaymentResponse;
import com.checkout.payment.gateway.bank.BankRestClient;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards against Jackson silently truncating floating-point payment amounts to Integer values.
 * Tests starting with Java request objects bypass JSON conversion and cannot catch this regression.
 * Uses the application's JSON configuration with a mocked bank client and repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentRequestJsonTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BankRestClient bankRestClient;

    @MockBean
    private PaymentsRepository paymentsRepository;

    @ParameterizedTest
    @ValueSource(doubles = {10.5, 100.0})
    void rejectsFloatingPointAmountsBeforeCallingBank(double amount) throws Exception {
        mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.status").value("Rejected"));

        verifyNoInteractions(bankRestClient, paymentsRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100, Integer.MAX_VALUE})
    void preservesIntegerAmounts(int amount) throws Exception {
        when(bankRestClient.sendPayment(any())).thenReturn(new BankPaymentResponse(true, "test-authorization"));

        mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request(amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Authorized"));

        verify(bankRestClient).sendPayment(argThat(payment -> payment.amount() == amount));
        verify(paymentsRepository).add(argThat(payment -> payment.amount() == amount));
    }

    @ParameterizedTest
    @ValueSource(longs = {2147483648L, -2147483649L})
    void rejectsAmountsOutsideIntegerRangeBeforeCallingBank(long amount) throws Exception {
        mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.status").value("Rejected"));

        verifyNoInteractions(bankRestClient, paymentsRepository);
    }

    private String request(Number amount) {
        return """
                {
                  "card_number": "2222405343248877",
                  "expiry_month": 12,
                  "expiry_year": 2030,
                  "currency": "GBP",
                  "amount": %s,
                  "cvv": "012"
                }
                """.formatted(amount);
    }
}
