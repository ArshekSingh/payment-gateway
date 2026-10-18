package com.arshek.paymentgateway.provider;

import com.arshek.paymentgateway.exception.PaymentProviderException;
import com.arshek.paymentgateway.util.CardUtils;
import com.arshek.paymentgateway.util.Crypto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-process stand-in for a real acquirer so the gateway runs end to end without external calls.
 * Behaviour is driven by well-known test card numbers.
 *
 * <ul>
 *   <li>4000000000000002 - card_declined</li>
 *   <li>4000000000009995 - insufficient_funds</li>
 *   <li>4000000000000069 - expired_card</li>
 *   <li>4000000000000119 - provider outage (throws PaymentProviderException)</li>
 *   <li>anything else that passes Luhn - approved</li>
 * </ul>
 *
 * Replace by implementing {@link PaymentProvider} for a real PSP and setting {@code gateway.provider}.
 */
@Component
@ConditionalOnProperty(prefix = "gateway", name = "provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResult authorize(AuthorizationRequest request) {
        String number = request.card().number();
        return switch (number) {
            case "4000000000000002" -> ProviderResult.declined("card_declined", "The card was declined.");
            case "4000000000009995" -> ProviderResult.declined("insufficient_funds", "The card has insufficient funds.");
            case "4000000000000069" -> ProviderResult.declined("expired_card", "The card has expired.");
            case "4000000000000119" -> throw new PaymentProviderException("Provider is temporarily unavailable");
            default -> ProviderResult.approved(reference("auth"), CardUtils.brandOf(number), CardUtils.last4(number));
        };
    }

    @Override
    public ProviderResult capture(String providerReference, long amount) {
        return ProviderResult.approved(reference("cap"));
    }

    @Override
    public ProviderResult voidAuthorization(String providerReference) {
        return ProviderResult.approved(reference("void"));
    }

    @Override
    public ProviderResult refund(String providerReference, long amount) {
        return ProviderResult.approved(reference("ref"));
    }

    private static String reference(String kind) {
        return "sim_" + Crypto.publicId(kind);
    }
}
