package com.arshek.paymentgateway.domain;

import com.arshek.paymentgateway.util.Crypto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Outbox row: written in the same transaction as the business change, delivered asynchronously. */
@Entity
@Table(name = "webhook_events", indexes = {
        @Index(name = "idx_webhook_events_due", columnList = "status, next_attempt_at"),
        @Index(name = "idx_webhook_events_merchant", columnList = "merchant_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10),
            Duration.ofHours(1), Duration.ofHours(6)
    };
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 40)
    private String publicId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private Long merchantId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @Column(nullable = false, updatable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public WebhookEvent(String publicId, Long merchantId, String eventType, String payload, Instant now) {
        this.publicId = publicId;
        this.merchantId = merchantId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = WebhookStatus.PENDING;
        this.nextAttemptAt = now;
    }

    public static String newPublicId() {
        return Crypto.publicId("evt");
    }

    /** Pushes the next attempt out so other dispatcher instances skip this row while it is in flight. */
    public void claim(Instant leaseUntil) {
        this.nextAttemptAt = leaseUntil;
    }

    public void markDelivered(Instant now, int httpStatus) {
        this.status = WebhookStatus.DELIVERED;
        this.attempts++;
        this.lastHttpStatus = httpStatus;
        this.lastError = null;
        this.deliveredAt = now;
    }

    public void recordFailure(String error, Integer httpStatus, Instant now, int maxAttempts) {
        this.attempts++;
        this.lastError = truncate(error);
        this.lastHttpStatus = httpStatus;
        if (attempts >= maxAttempts) {
            this.status = WebhookStatus.FAILED;
        } else {
            this.nextAttemptAt = now.plus(BACKOFF[Math.min(attempts - 1, BACKOFF.length - 1)]);
        }
    }

    public void abandon(String reason) {
        this.status = WebhookStatus.FAILED;
        this.lastError = truncate(reason);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
