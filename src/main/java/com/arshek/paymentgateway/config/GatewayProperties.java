package com.arshek.paymentgateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        @DefaultValue("simulated") String provider,
        @DefaultValue("") String adminApiKey,
        @DefaultValue("99999999") long maxAmount,
        @DefaultValue Webhook webhook) {

    public record Webhook(
            @DefaultValue("true") boolean dispatcherEnabled,
            @DefaultValue("50") int batchSize,
            @DefaultValue("6") int maxAttempts,
            @DefaultValue("5s") Duration timeout,
            @DefaultValue("2m") Duration lease,
            @DefaultValue("false") boolean allowPrivateTargets) {
    }
}
