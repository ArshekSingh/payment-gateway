package com.arshek.paymentgateway.dto;

import com.arshek.paymentgateway.domain.RefundStatus;
import java.time.Instant;

public record RefundResponse(
        String id,
        String paymentId,
        long amount,
        RefundStatus status,
        String reason,
        String failureCode,
        Instant createdAt) {
}
