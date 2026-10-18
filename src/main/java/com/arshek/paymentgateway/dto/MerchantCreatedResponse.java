package com.arshek.paymentgateway.dto;

import com.arshek.paymentgateway.domain.MerchantStatus;
import java.time.Instant;

/** Returned once at onboarding. {@code apiKey} is not recoverable afterwards (only its hash is stored). */
public record MerchantCreatedResponse(
        String id,
        String name,
        String email,
        MerchantStatus status,
        String apiKey,
        String webhookSecret,
        String webhookUrl,
        Instant createdAt) {
}
