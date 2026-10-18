package com.arshek.paymentgateway.domain;

import com.arshek.paymentgateway.util.Crypto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "merchants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 40)
    private String publicId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Column(name = "api_key_prefix", nullable = false, length = 20)
    private String apiKeyPrefix;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", nullable = false, length = 100)
    private String webhookSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Merchant(String name, String email, String apiKeyHash, String apiKeyPrefix,
                    String webhookSecret, String webhookUrl) {
        this.publicId = Crypto.publicId("mer");
        this.name = name;
        this.email = email;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
        this.webhookSecret = webhookSecret;
        this.webhookUrl = webhookUrl;
        this.status = MerchantStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == MerchantStatus.ACTIVE;
    }

    public void updateWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
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
