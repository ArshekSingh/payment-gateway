package com.arshek.paymentgateway.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends GatewayException {

    public InvalidRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
