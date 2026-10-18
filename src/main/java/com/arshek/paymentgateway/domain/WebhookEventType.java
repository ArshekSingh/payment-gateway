package com.arshek.paymentgateway.domain;

public enum WebhookEventType {
    PAYMENT_AUTHORIZED("payment.authorized"),
    PAYMENT_CAPTURED("payment.captured"),
    PAYMENT_FAILED("payment.failed"),
    PAYMENT_CANCELLED("payment.cancelled"),
    REFUND_SUCCEEDED("refund.succeeded"),
    REFUND_FAILED("refund.failed");

    private final String value;

    WebhookEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
