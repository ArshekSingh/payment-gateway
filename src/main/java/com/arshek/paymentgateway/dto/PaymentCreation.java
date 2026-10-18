package com.arshek.paymentgateway.dto;

/** Result of a create call; {@code replayed} is true when an idempotency key matched an earlier request. */
public record PaymentCreation(PaymentResponse payment, boolean replayed) {
}
