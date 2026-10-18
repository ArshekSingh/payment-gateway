package com.arshek.paymentgateway.service;

public record DeliveryResult(boolean success, Integer httpStatus, String error) {

    public static DeliveryResult success(int httpStatus) {
        return new DeliveryResult(true, httpStatus, null);
    }

    public static DeliveryResult failure(Integer httpStatus, String error) {
        return new DeliveryResult(false, httpStatus, error);
    }
}
