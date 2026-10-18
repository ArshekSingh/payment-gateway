package com.arshek.paymentgateway.provider;

import com.arshek.paymentgateway.dto.CardDetails;

public record AuthorizationRequest(String paymentId, long amount, String currency, CardDetails card) {
}
