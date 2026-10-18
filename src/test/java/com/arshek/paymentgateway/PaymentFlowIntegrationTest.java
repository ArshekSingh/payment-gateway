package com.arshek.paymentgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arshek.paymentgateway.repository.WebhookEventRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    private static final String ADMIN_KEY = "test-admin-key";
    private static final String GOOD_CARD = "4242424242424242";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private WebhookEventRepository webhookEventRepository;

    private String apiKey;
    private String merchantId;

    @BeforeEach
    void onboardMerchant() throws Exception {
        String body = mvc.perform(post("/admin/merchants")
                        .header("X-Admin-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"email\":\"" + UUID.randomUUID() + "@example.com\","
                                + "\"webhookUrl\":\"http://localhost:9/hook\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").exists())
                .andReturn().getResponse().getContentAsString();
        apiKey = JsonPath.read(body, "$.apiKey");
        merchantId = JsonPath.read(body, "$.id");
    }

    @Test
    void rejectsUnauthenticatedAndWrongRole() throws Exception {
        mvc.perform(get("/v1/payments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/payments").header("Authorization", "Bearer sk_nope"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/merchants").header("X-Admin-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // a merchant key is not an admin credential
        mvc.perform(get("/admin/merchants").header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullLifecycleCreateConfirmCaptureRefund() throws Exception {
        String paymentId = createPayment(2500, true, null);

        mvc.perform(post("/v1/payments/{id}/confirm", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content(card(GOOD_CARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.cardLast4").value("4242"))
                .andExpect(jsonPath("$.cardBrand").value("visa"));

        mvc.perform(post("/v1/payments/{id}/refunds", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000,\"reason\":\"damaged\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mvc.perform(get("/v1/payments/{id}", paymentId).headers(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.amountRefunded").value(1000));

        // over-refund is rejected, full remaining balance (no body) succeeds
        mvc.perform(post("/v1/payments/{id}/refunds", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1501}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("refund_exceeds_balance"));
        mvc.perform(post("/v1/payments/{id}/refunds", paymentId).headers(auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1500));

        mvc.perform(get("/v1/payments/{id}/refunds", paymentId).headers(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // authorized, captured, refund.succeeded x2
        assertThat(webhookEventRepository.countByMerchantId(merchantDbId())).isEqualTo(4);
    }

    @Test
    void manualCaptureAndCancelRules() throws Exception {
        String paymentId = createPayment(900, false, null);
        mvc.perform(post("/v1/payments/{id}/confirm", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content(card(GOOD_CARD)))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));
        mvc.perform(post("/v1/payments/{id}/capture", paymentId).headers(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        mvc.perform(post("/v1/payments/{id}/cancel", paymentId).headers(auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid_payment_state"));
    }

    @Test
    void declinedCardMarksPaymentFailedAndCanBeRetried() throws Exception {
        String paymentId = createPayment(1200, true, null);
        mvc.perform(post("/v1/payments/{id}/confirm", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content(card("4000000000000002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("card_declined"));

        mvc.perform(post("/v1/payments/{id}/confirm", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content(card(GOOD_CARD)))
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.failureCode").doesNotExist());
    }

    @Test
    void providerOutageSurfacesAsBadGatewayWithoutChangingState() throws Exception {
        String paymentId = createPayment(1200, true, null);
        mvc.perform(post("/v1/payments/{id}/confirm", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content(card("4000000000000119")))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/v1/payments/{id}", paymentId).headers(auth()))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void idempotencyKeyReplaysSameRequestAndRejectsDifferentOne() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = createPayment(700, true, key);

        String body = mvc.perform(post("/v1/payments").headers(auth())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":700,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.id")).isEqualTo(first);

        mvc.perform(post("/v1/payments").headers(auth())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":701,\"currency\":\"USD\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/v1/payments").headers(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"currency\":\"usd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details.amount").exists())
                .andExpect(jsonPath("$.details.currency").exists());

        mvc.perform(post("/v1/payments").headers(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"ZZZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_currency"));

        String paymentId = createPayment(100, true, null);
        mvc.perform(post("/v1/payments/{id}/confirm", paymentId).headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content(card("4242424242424241")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['card.number']").exists());
    }

    @Test
    void merchantsCannotSeeEachOthersPayments() throws Exception {
        String paymentId = createPayment(100, true, null);

        String otherBody = mvc.perform(post("/admin/merchants").header("X-Admin-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"email\":\"" + UUID.randomUUID() + "@example.com\"}"))
                .andReturn().getResponse().getContentAsString();
        String otherKey = JsonPath.read(otherBody, "$.apiKey");

        mvc.perform(get("/v1/payments/{id}", paymentId).header("Authorization", "Bearer " + otherKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void webhookUrlIsValidatedOnUpdate() throws Exception {
        mvc.perform(put("/v1/merchant/webhook").headers(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"ftp://example.com/x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_webhook_url"));
        mvc.perform(put("/v1/merchant/webhook").headers(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookUrl").doesNotExist());
    }

    // ---- helpers ----

    private String createPayment(long amount, boolean autoCapture, String idempotencyKey) throws Exception {
        var request = post("/v1/payments").headers(auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"currency\":\"USD\",\"captureAutomatically\":" + autoCapture + "}");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        String body = mvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private org.springframework.http.HttpHeaders auth() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private static String card(String number) {
        return "{\"card\":{\"number\":\"" + number + "\",\"expMonth\":12,\"expYear\":2099,\"cvc\":\"123\"}}";
    }

    private Long merchantDbId() throws Exception {
        // the public id is what the API exposes; resolve the numeric id through the admin listing
        String body = mvc.perform(get("/admin/merchants?size=100").header("X-Admin-Key", ADMIN_KEY))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(merchantId);
        return merchantRepository.findAll().stream()
                .filter(m -> m.getPublicId().equals(merchantId)).findFirst().orElseThrow().getId();
    }

    @Autowired
    private com.arshek.paymentgateway.repository.MerchantRepository merchantRepository;
}
