package com.arshek.paymentgateway.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code amount} is in minor units; omit it to refund the full remaining balance. */
public record CreateRefundRequest(
        @Positive Long amount,
        @Size(max = 255) String reason) {
}
