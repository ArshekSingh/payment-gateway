package com.arshek.paymentgateway.exception;

import org.springframework.http.HttpStatus;

/** The upstream acquirer/provider failed in a way that is not a normal decline. */
public class PaymentProviderException extends GatewayException {

    public PaymentProviderException(String message) {
        super(HttpStatus.BAD_GATEWAY, "provider_error", message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }
}
