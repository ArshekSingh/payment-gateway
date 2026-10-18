package com.arshek.paymentgateway.web;

import com.arshek.paymentgateway.dto.CreateRefundRequest;
import com.arshek.paymentgateway.dto.PageResponse;
import com.arshek.paymentgateway.dto.RefundCreation;
import com.arshek.paymentgateway.dto.RefundResponse;
import com.arshek.paymentgateway.security.MerchantPrincipal;
import com.arshek.paymentgateway.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/v1/payments/{paymentId}/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    public ResponseEntity<RefundResponse> create(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @PathVariable String paymentId,
            @Valid @RequestBody(required = false) CreateRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CreateRefundRequest body = request != null ? request : new CreateRefundRequest(null, null);
        RefundCreation created = refundService.create(merchant.id(), paymentId, body, idempotencyKey);
        return ResponseEntity.status(created.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(created.refund());
    }

    @GetMapping
    public PageResponse<RefundResponse> list(@AuthenticationPrincipal MerchantPrincipal merchant,
                                             @PathVariable String paymentId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return refundService.list(merchant.id(), paymentId, PageRequests.newestFirst(page, size));
    }
}
