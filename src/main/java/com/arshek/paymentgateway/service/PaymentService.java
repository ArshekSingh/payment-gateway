package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.config.GatewayProperties;
import com.arshek.paymentgateway.domain.Merchant;
import com.arshek.paymentgateway.domain.Payment;
import com.arshek.paymentgateway.domain.PaymentStatus;
import com.arshek.paymentgateway.domain.WebhookEventType;
import com.arshek.paymentgateway.dto.CardDetails;
import com.arshek.paymentgateway.dto.CreatePaymentRequest;
import com.arshek.paymentgateway.dto.PageResponse;
import com.arshek.paymentgateway.dto.PaymentCreation;
import com.arshek.paymentgateway.dto.PaymentResponse;
import com.arshek.paymentgateway.exception.ConflictException;
import com.arshek.paymentgateway.exception.IdempotencyConflictException;
import com.arshek.paymentgateway.exception.InvalidRequestException;
import com.arshek.paymentgateway.exception.PaymentProviderException;
import com.arshek.paymentgateway.exception.ResourceNotFoundException;
import com.arshek.paymentgateway.provider.AuthorizationRequest;
import com.arshek.paymentgateway.provider.PaymentProvider;
import com.arshek.paymentgateway.provider.ProviderResult;
import com.arshek.paymentgateway.repository.MerchantRepository;
import com.arshek.paymentgateway.repository.PaymentRepository;
import com.arshek.paymentgateway.util.Crypto;
import com.arshek.paymentgateway.util.IdempotencyKeys;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Currency;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment lifecycle: create, confirm (authorize [+ capture]), capture, cancel.
 *
 * <p>Trade-off: the provider call happens inside the database transaction, holding a row lock on the
 * payment. That keeps the state machine simple and prevents double-confirms, but ties transaction
 * time to provider latency. With a real PSP, move to an explicit PENDING state and finish the call
 * outside the transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentProvider provider;
    private final WebhookService webhookService;
    private final PaymentMapper mapper;
    private final GatewayProperties properties;
    private final Clock clock;

    @Transactional
    public PaymentCreation create(Long merchantId, CreatePaymentRequest request, String idempotencyKey) {
        validateCurrencyAndAmount(request);
        String key = IdempotencyKeys.normalize(idempotencyKey);
        String fingerprint = fingerprint(request);

        if (key != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(merchantId, key);
            if (existing.isPresent()) {
                if (!existing.get().getRequestFingerprint().equals(fingerprint)) {
                    throw new IdempotencyConflictException();
                }
                return new PaymentCreation(mapper.toResponse(existing.get()), true);
            }
        }

        Merchant merchant = merchantRepository.getReferenceById(merchantId);
        Payment payment = paymentRepository.save(new Payment(merchant, request.amount(), request.currency(),
                request.captureAutomaticallyOrDefault(), request.description(), request.merchantReference(),
                key, fingerprint));
        return new PaymentCreation(mapper.toResponse(payment), false);
    }

    @Transactional
    public PaymentResponse confirm(Long merchantId, String paymentId, CardDetails card) {
        Payment payment = lock(merchantId, paymentId);
        if (!payment.canConfirm()) {
            throw new ConflictException("invalid_payment_state",
                    "Cannot confirm a payment in state " + payment.getStatus().name().toLowerCase());
        }
        if (YearMonth.of(card.expYear(), card.expMonth()).isBefore(YearMonth.now(clock))) {
            throw new InvalidRequestException("card_expired", "The card has expired");
        }

        ProviderResult authorization = provider.authorize(
                new AuthorizationRequest(payment.getPublicId(), payment.getAmount(), payment.getCurrency(), card));

        if (!authorization.approved()) {
            payment.fail(authorization.declineCode(), authorization.declineMessage());
            publish(payment, WebhookEventType.PAYMENT_FAILED);
            return mapper.toResponse(payment);
        }

        payment.authorize(authorization.reference(), authorization.cardBrand(), authorization.cardLast4());
        publish(payment, WebhookEventType.PAYMENT_AUTHORIZED);

        if (payment.isCaptureAutomatically()) {
            captureAfterAuthorization(payment);
        }
        return mapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse capture(Long merchantId, String paymentId) {
        Payment payment = lock(merchantId, paymentId);
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new ConflictException("invalid_payment_state",
                    "Cannot capture a payment in state " + payment.getStatus().name().toLowerCase());
        }
        ProviderResult result = provider.capture(payment.getProviderReference(), payment.getAmount());
        if (!result.approved()) {
            throw new PaymentProviderException("Capture was rejected by the provider: " + result.declineCode());
        }
        payment.capture();
        publish(payment, WebhookEventType.PAYMENT_CAPTURED);
        return mapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse cancel(Long merchantId, String paymentId) {
        Payment payment = lock(merchantId, paymentId);
        boolean hadAuthorization = payment.getStatus() == PaymentStatus.AUTHORIZED;
        payment.cancel(); // validates the transition before anything leaves the system
        if (hadAuthorization) {
            ProviderResult result = provider.voidAuthorization(payment.getProviderReference());
            if (!result.approved()) {
                throw new PaymentProviderException("Authorization could not be voided: " + result.declineCode());
            }
        }
        publish(payment, WebhookEventType.PAYMENT_CANCELLED);
        return mapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long merchantId, String paymentId) {
        return paymentRepository.findOwned(paymentId, merchantId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(Long merchantId, PaymentStatus status, Pageable pageable) {
        var page = status == null
                ? paymentRepository.findByMerchant(merchantId, pageable)
                : paymentRepository.findByMerchantAndStatus(merchantId, status, pageable);
        return PageResponse.from(page.map(mapper::toResponse));
    }

    /** Authorization already succeeded at the provider, so a failed capture must not undo it. */
    private void captureAfterAuthorization(Payment payment) {
        try {
            ProviderResult capture = provider.capture(payment.getProviderReference(), payment.getAmount());
            if (capture.approved()) {
                payment.capture();
                publish(payment, WebhookEventType.PAYMENT_CAPTURED);
            } else {
                log.warn("Automatic capture rejected for payment {}: {}", payment.getPublicId(), capture.declineCode());
            }
        } catch (PaymentProviderException e) {
            log.warn("Automatic capture failed for payment {}; it stays authorized and can be captured manually",
                    payment.getPublicId(), e);
        }
    }

    private void publish(Payment payment, WebhookEventType type) {
        webhookService.enqueue(payment.getMerchant(), type, mapper.toResponse(payment));
    }

    private Payment lock(Long merchantId, String paymentId) {
        return paymentRepository.findOwnedForUpdate(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    private void validateCurrencyAndAmount(CreatePaymentRequest request) {
        try {
            Currency.getInstance(request.currency());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("invalid_currency", "Unsupported currency: " + request.currency());
        }
        if (request.amount() > properties.maxAmount()) {
            throw new InvalidRequestException("amount_too_large",
                    "Amount exceeds the maximum of " + properties.maxAmount());
        }
    }

    private static String fingerprint(CreatePaymentRequest r) {
        return Crypto.sha256Hex(String.join("|", String.valueOf(r.amount()), r.currency(),
                String.valueOf(r.description()), String.valueOf(r.merchantReference()),
                String.valueOf(r.captureAutomaticallyOrDefault())));
    }
}
