package com.arshek.paymentgateway.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends GatewayException {

    public ResourceNotFoundException(String resource, String id) {
        super(HttpStatus.NOT_FOUND, "resource_not_found", resource + " '" + id + "' was not found");
    }
}
