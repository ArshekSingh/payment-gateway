package com.arshek.paymentgateway.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends GatewayException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
