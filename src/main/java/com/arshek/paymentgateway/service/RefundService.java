package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.domain.Payment;
import com.arshek.paymentgateway.domain.Refund;
import com.arshek.paymentgateway.domain.WebhookEventType;
import com.arshek.paymentgateway.dto.CreateRefundRequest;
import com.arshek.paymentgateway.dto.PageResponse;
import com.arshek.paymentgateway.dto.RefundCreation;
import com.arshek.paymentgateway.dto.RefundResponse;
import com.arshek.paymentgateway.exception.ConflictException;
import com.arshek.paymentgateway.exception.IdempotencyConflictException;
import com.arshek.paymentgateway.exception.InvalidRequestException;
import com.arshek.paymentgateway.exception.ResourceNotFoundException;
import com.arshek.paymentgateway.provider.PaymentProvider;
import com.arshek.paymentgateway.provider.ProviderResult;
import com.arshek.paymentgateway.repository.PaymentRepository;
import com.arshek.paymentgateway.repository.RefundRepository;
import com.arshek.paymentgateway.util.IdempotencyKeys;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentProvider provider;
    private final WebhookService webhookService;
    private final PaymentMapper mapper;

    @Transactional
    public RefundCreation create(Long merchantId, String paymentId, CreateRefundRequest request,
                                 String idempotencyKey) {
        // The payment row lock serialises concurrent refunds so the balance check below is race-free.
        Payment payment = paymentRepository.findOwnedForUpdate(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        String key = IdempotencyKeys.normalize(idempotencyKey);

        if (key != null) {
            Optional<Refund> existing = refundRepository.findByIdempotencyKey(payment.getId(), key);
            if (existing.isPresent()) {
                Refund previous = existing.get();
                boolean sameAmount = request.amount() == null || request.amount() == previous.getAmount();
                boolean sameReason = Objects.equals(request.reason(), previous.getReason());
                if (!sameAmount || !sameReason) {
                    throw new IdempotencyConflictException();
                }
                return new RefundCreation(mapper.toResponse(previous, payment.getPublicId()), true);
            }
        }

        long refundable = payment.refundableAmount();
        if (refundable <= 0) {
            throw new ConflictException("nothing_to_refund",
                    "Payment in state " + payment.getStatus().name().toLowerCase() + " has no refundable balance");
        }
        long amount = request.amount() != null ? request.amount() : refundable;
        if (amount > refundable) {
            throw new InvalidRequestException("refund_exceeds_balance",
                    "Refund amount exceeds the refundable balance of " + refundable);
        }

        ProviderResult result = provider.refund(payment.getProviderReference(), amount);
        Refund refund;
        if (result.approved()) {
            payment.registerRefund(amount);
            refund = refundRepository.save(
                    Refund.succeeded(payment, amount, request.reason(), key, result.reference()));
            webhookService.enqueue(payment.getMerchant(), WebhookEventType.REFUND_SUCCEEDED,
                    mapper.toResponse(refund, payment.getPublicId()));
        } else {
            refund = refundRepository.save(
                    Refund.failed(payment, amount, request.reason(), key, result.declineCode()));
            webhookService.enqueue(payment.getMerchant(), WebhookEventType.REFUND_FAILED,
                    mapper.toResponse(refund, payment.getPublicId()));
        }
        return new RefundCreation(mapper.toResponse(refund, payment.getPublicId()), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> list(Long merchantId, String paymentId, Pageable pageable) {
        Payment payment = paymentRepository.findOwned(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return PageResponse.from(refundRepository.findByPayment(payment.getId(), pageable)
                .map(r -> mapper.toResponse(r, payment.getPublicId())));
    }

}
