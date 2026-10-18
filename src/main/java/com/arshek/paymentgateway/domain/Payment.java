package com.arshek.paymentgateway.domain;

import com.arshek.paymentgateway.exception.ConflictException;
import com.arshek.paymentgateway.exception.InvalidRequestException;
import com.arshek.paymentgateway.util.Crypto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Payment aggregate. All state transitions go through the methods below so illegal
 * transitions (e.g. capturing a failed payment) are rejected in one place.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_merchant_created", columnList = "merchant_id, created_at"),
        @Index(name = "idx_payments_merchant_status", columnList = "merchant_id, status"),
        @Index(name = "idx_payments_merchant_reference", columnList = "merchant_id, merchant_reference")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 40)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, updatable = false)
    private Merchant merchant;

    /** Amount in the currency's minor unit (e.g. cents / paise). */
    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "capture_automatically", nullable = false, updatable = false)
    private boolean captureAutomatically;

    @Column(length = 255)
    private String description;

    @Column(name = "merchant_reference", length = 100)
    private String merchantReference;

    @Column(name = "idempotency_key", updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Column(name = "amount_refunded", nullable = false)
    private long amountRefunded;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    public Payment(Merchant merchant, long amount, String currency, boolean captureAutomatically,
                   String description, String merchantReference, String idempotencyKey,
                   String requestFingerprint) {
        this.publicId = Crypto.publicId("pay");
        this.merchant = merchant;
        this.amount = amount;
        this.currency = currency;
        this.captureAutomatically = captureAutomatically;
        this.description = description;
        this.merchantReference = merchantReference;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = PaymentStatus.CREATED;
    }

    public boolean canConfirm() {
        return status == PaymentStatus.CREATED || status == PaymentStatus.FAILED;
    }

    public void authorize(String providerReference, String cardBrand, String cardLast4) {
        requireState("authorize", PaymentStatus.CREATED, PaymentStatus.FAILED);
        this.status = PaymentStatus.AUTHORIZED;
        this.providerReference = providerReference;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.failureCode = null;
        this.failureMessage = null;
        this.authorizedAt = Instant.now();
    }

    public void fail(String code, String message) {
        requireState("fail", PaymentStatus.CREATED, PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
    }

    public void capture() {
        requireState("capture", PaymentStatus.AUTHORIZED);
        this.status = PaymentStatus.CAPTURED;
        this.capturedAt = Instant.now();
    }

    public void cancel() {
        requireState("cancel", PaymentStatus.CREATED, PaymentStatus.AUTHORIZED, PaymentStatus.FAILED);
        this.status = PaymentStatus.CANCELLED;
    }

    public long refundableAmount() {
        if (status != PaymentStatus.CAPTURED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            return 0;
        }
        return amount - amountRefunded;
    }

    public void registerRefund(long refundAmount) {
        requireState("refund", PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED);
        if (refundAmount <= 0) {
            throw new InvalidRequestException("invalid_amount", "Refund amount must be positive");
        }
        if (refundAmount > refundableAmount()) {
            throw new InvalidRequestException("refund_exceeds_balance",
                    "Refund amount exceeds the refundable balance of " + refundableAmount());
        }
        this.amountRefunded += refundAmount;
        this.status = amountRefunded == amount ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
    }

    private void requireState(String action, PaymentStatus... allowed) {
        for (PaymentStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new ConflictException("invalid_payment_state",
                "Cannot " + action + " a payment in state " + status.name().toLowerCase());
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
