package com.arshek.paymentgateway.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends GatewayException {

    public IdempotencyConflictException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency_key_reused",
                "This Idempotency-Key was already used with different request parameters");
    }
}
