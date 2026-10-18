package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.config.GatewayProperties;
import com.arshek.paymentgateway.domain.Merchant;
import com.arshek.paymentgateway.domain.WebhookEvent;
import com.arshek.paymentgateway.domain.WebhookStatus;
import com.arshek.paymentgateway.repository.MerchantRepository;
import com.arshek.paymentgateway.repository.WebhookEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the outbox and delivers webhooks. Work is split in three steps so no database lock is held
 * during network I/O: (1) claim due rows (SKIP LOCKED + lease), (2) deliver, (3) record the outcome.
 * A crashed instance simply lets the lease expire and the event is retried.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gateway.webhook", name = "dispatcher-enabled", havingValue = "true",
        matchIfMissing = true)
public class WebhookDispatcher {

    private record DeliveryTask(Long eventId, String publicId, String url, String secret, String payload) {
    }

    private final WebhookEventRepository eventRepository;
    private final MerchantRepository merchantRepository;
    private final WebhookDeliveryClient deliveryClient;
    private final GatewayProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public WebhookDispatcher(WebhookEventRepository eventRepository, MerchantRepository merchantRepository,
                             WebhookDeliveryClient deliveryClient, GatewayProperties properties, Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.merchantRepository = merchantRepository;
        this.deliveryClient = deliveryClient;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${gateway.webhook.poll-interval-ms:5000}")
    public void dispatchDue() {
        for (DeliveryTask task : claimDue()) {
            try {
                DeliveryResult result = deliveryClient.deliver(task.url(), task.secret(), task.publicId(), task.payload());
                recordOutcome(task, result);
            } catch (RuntimeException e) {
                // keep the batch going; the lease expiry will make the event eligible again
                log.error("Unexpected error delivering webhook {}", task.publicId(), e);
            }
        }
    }

    private List<DeliveryTask> claimDue() {
        List<DeliveryTask> claimed = tx.execute(status -> {
            Instant now = clock.instant();
            List<WebhookEvent> due = eventRepository.findDue(WebhookStatus.PENDING, now,
                    PageRequest.of(0, properties.webhook().batchSize()));
            if (due.isEmpty()) {
                return List.<DeliveryTask>of();
            }
            Map<Long, Merchant> merchants = merchantRepository
                    .findAllById(due.stream().map(WebhookEvent::getMerchantId).collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(Merchant::getId, Function.identity()));
            List<DeliveryTask> tasks = new ArrayList<>(due.size());
            for (WebhookEvent event : due) {
                Merchant merchant = merchants.get(event.getMerchantId());
                if (merchant == null || merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()) {
                    event.abandon("Merchant has no webhook endpoint configured");
                    continue;
                }
                event.claim(now.plus(properties.webhook().lease()));
                tasks.add(new DeliveryTask(event.getId(), event.getPublicId(), merchant.getWebhookUrl(),
                        merchant.getWebhookSecret(), event.getPayload()));
            }
            return tasks;
        });
        return claimed == null ? List.of() : claimed;
    }

    private void recordOutcome(DeliveryTask task, DeliveryResult result) {
        tx.executeWithoutResult(status -> eventRepository.findById(task.eventId()).ifPresent(event -> {
            Instant now = clock.instant();
            if (result.success()) {
                event.markDelivered(now, result.httpStatus());
            } else {
                event.recordFailure(result.error(), result.httpStatus(), now, properties.webhook().maxAttempts());
                log.warn("Webhook {} delivery failed (attempt {}): {}", task.publicId(), event.getAttempts(),
                        result.error());
            }
        }));
    }
}
