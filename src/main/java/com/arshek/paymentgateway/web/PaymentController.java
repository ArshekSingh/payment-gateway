package com.arshek.paymentgateway.web;

import com.arshek.paymentgateway.domain.PaymentStatus;
import com.arshek.paymentgateway.dto.ConfirmPaymentRequest;
import com.arshek.paymentgateway.dto.CreatePaymentRequest;
import com.arshek.paymentgateway.dto.PageResponse;
import com.arshek.paymentgateway.dto.PaymentCreation;
import com.arshek.paymentgateway.dto.PaymentResponse;
import com.arshek.paymentgateway.security.MerchantPrincipal;
import com.arshek.paymentgateway.service.PaymentService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentCreation created = paymentService.create(merchant.id(), request, idempotencyKey);
        if (created.replayed()) {
            return ResponseEntity.ok(created.payment());
        }
        return ResponseEntity.created(URI.create("/v1/payments/" + created.payment().id())).body(created.payment());
    }

    @GetMapping
    public PageResponse<PaymentResponse> list(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.list(merchant.id(), status, PageRequests.newestFirst(page, size));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@AuthenticationPrincipal MerchantPrincipal merchant, @PathVariable String paymentId) {
        return paymentService.get(merchant.id(), paymentId);
    }

    @PostMapping("/{paymentId}/confirm")
    public PaymentResponse confirm(@AuthenticationPrincipal MerchantPrincipal merchant,
                                   @PathVariable String paymentId,
                                   @Valid @RequestBody ConfirmPaymentRequest request) {
        return paymentService.confirm(merchant.id(), paymentId, request.card());
    }

    @PostMapping("/{paymentId}/capture")
    public PaymentResponse capture(@AuthenticationPrincipal MerchantPrincipal merchant,
                                   @PathVariable String paymentId) {
        return paymentService.capture(merchant.id(), paymentId);
    }

    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancel(@AuthenticationPrincipal MerchantPrincipal merchant,
                                  @PathVariable String paymentId) {
        return paymentService.cancel(merchant.id(), paymentId);
    }
}
