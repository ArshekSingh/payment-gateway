package com.arshek.paymentgateway.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refunds_payment_created", columnList = "payment_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 40)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private RefundStatus status;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "provider_reference", length = 100, updatable = false)
    private String providerReference;

    @Column(name = "failure_code", length = 60, updatable = false)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Refund(Payment payment, long amount, RefundStatus status, String reason, String idempotencyKey,
                   String providerReference, String failureCode) {
        this.publicId = Crypto.publicId("ref");
        this.payment = payment;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.providerReference = providerReference;
        this.failureCode = failureCode;
    }

    public static Refund succeeded(Payment payment, long amount, String reason, String idempotencyKey,
                                   String providerReference) {
        return new Refund(payment, amount, RefundStatus.SUCCEEDED, reason, idempotencyKey, providerReference, null);
    }

    public static Refund failed(Payment payment, long amount, String reason, String idempotencyKey,
                                String failureCode) {
        return new Refund(payment, amount, RefundStatus.FAILED, reason, idempotencyKey, null, failureCode);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
