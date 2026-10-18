package com.arshek.paymentgateway.provider;

/**
 * Port to the upstream acquirer / card network. Implementations signal a normal decline through
 * {@link ProviderResult#declined} and throw {@code PaymentProviderException} for infrastructure failures.
 */
public interface PaymentProvider {

    ProviderResult authorize(AuthorizationRequest request);

    ProviderResult capture(String providerReference, long amount);

    ProviderResult voidAuthorization(String providerReference);

    ProviderResult refund(String providerReference, long amount);
}
