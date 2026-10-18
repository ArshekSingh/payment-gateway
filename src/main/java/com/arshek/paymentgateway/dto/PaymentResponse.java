package com.arshek.paymentgateway.dto;

import com.arshek.paymentgateway.domain.PaymentStatus;
import java.time.Instant;

public record PaymentResponse(
        String id,
        long amount,
        String currency,
        PaymentStatus status,
        String description,
        String merchantReference,
        boolean captureAutomatically,
        String cardBrand,
        String cardLast4,
        long amountRefunded,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant authorizedAt,
        Instant capturedAt) {
}
