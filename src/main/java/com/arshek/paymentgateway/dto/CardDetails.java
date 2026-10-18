package com.arshek.paymentgateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Raw card data. Never persisted and never logged; only brand and last4 are stored. */
public record CardDetails(
        @NotBlank @ValidCardNumber String number,
        @Min(1) @Max(12) int expMonth,
        @Min(2000) @Max(2100) int expYear,
        @NotBlank @Pattern(regexp = "^\\d{3,4}$", message = "must be 3 or 4 digits") String cvc,
        @Size(max = 100) String holderName) {

    @Override
    public String toString() {
        return "CardDetails[redacted]";
    }
}
