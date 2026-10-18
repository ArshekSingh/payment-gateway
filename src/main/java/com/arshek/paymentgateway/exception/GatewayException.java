package com.arshek.paymentgateway.exception;

import org.springframework.http.HttpStatus;

/** Base type for errors that map directly to an API response. */
public abstract class GatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected GatewayException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
