package com.arshek.paymentgateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(@NotNull @Valid CardDetails card) {
}
