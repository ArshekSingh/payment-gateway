package com.arshek.paymentgateway.dto;

import jakarta.validation.constraints.Size;

/** A null or blank {@code webhookUrl} disables webhook delivery. */
public record UpdateWebhookRequest(@Size(max = 500) String webhookUrl) {
}
