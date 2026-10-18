package com.arshek.paymentgateway.dto;

import com.arshek.paymentgateway.domain.MerchantStatus;
import java.time.Instant;

public record MerchantResponse(
        String id,
        String name,
        String email,
        MerchantStatus status,
        String apiKeyPrefix,
        String webhookUrl,
        Instant createdAt) {
}
