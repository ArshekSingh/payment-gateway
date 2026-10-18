package com.arshek.paymentgateway.provider;

public record ProviderResult(
        boolean approved,
        String reference,
        String cardBrand,
        String cardLast4,
        String declineCode,
        String declineMessage) {

    public static ProviderResult approved(String reference, String cardBrand, String cardLast4) {
        return new ProviderResult(true, reference, cardBrand, cardLast4, null, null);
    }

    public static ProviderResult approved(String reference) {
        return approved(reference, null, null);
    }

    public static ProviderResult declined(String code, String message) {
        return new ProviderResult(false, null, null, null, code, message);
    }
}
