package com.arshek.paymentgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @Positive long amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter uppercase ISO 4217 code") String currency,
        @Size(max = 255) String description,
        @Size(max = 100) String merchantReference,
        Boolean captureAutomatically) {

    public boolean captureAutomaticallyOrDefault() {
        return captureAutomatically == null || captureAutomatically;
    }
}
