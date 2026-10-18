package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.domain.Merchant;
import com.arshek.paymentgateway.domain.WebhookEvent;
import com.arshek.paymentgateway.domain.WebhookEventType;
import com.arshek.paymentgateway.repository.WebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes webhook events to the outbox table inside the caller's transaction. */
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private record Envelope(String id, String type, long created, Object data) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Merchant merchant, WebhookEventType type, Object data) {
        if (merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()) {
            return;
        }
        String eventId = WebhookEvent.newPublicId();
        String payload = serialize(new Envelope(eventId, type.value(), clock.instant().getEpochSecond(), data));
        webhookEventRepository.save(new WebhookEvent(eventId, merchant.getId(), type.value(), payload, clock.instant()));
    }

    private String serialize(Envelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise webhook payload", e);
        }
    }
}
