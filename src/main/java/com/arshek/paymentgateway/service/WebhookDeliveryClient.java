package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.config.GatewayProperties;
import com.arshek.paymentgateway.exception.InvalidRequestException;
import com.arshek.paymentgateway.util.Crypto;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Sends one signed webhook. The signature header is {@code t=<unix seconds>,v1=<hex>} where the
 * HMAC-SHA256 is computed over {@code "<t>.<body>"} with the merchant's webhook secret, so
 * receivers can reject replays by checking the timestamp.
 */
@Component
public class WebhookDeliveryClient {

    static final String SIGNATURE_HEADER = "X-Gateway-Signature";
    static final String EVENT_ID_HEADER = "X-Gateway-Event-Id";

    private final HttpClient httpClient;
    private final Duration timeout;
    private final WebhookUrlValidator urlValidator;
    private final Clock clock;

    public WebhookDeliveryClient(GatewayProperties properties, WebhookUrlValidator urlValidator, Clock clock) {
        this.timeout = properties.webhook().timeout();
        this.urlValidator = urlValidator;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DeliveryResult deliver(String url, String secret, String eventId, String payload) {
        try {
            urlValidator.validate(url);
            long timestamp = clock.instant().getEpochSecond();
            String signature = "t=" + timestamp + ",v1=" + Crypto.hmacSha256Hex(secret, timestamp + "." + payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PaymentGateway-Webhook/1.0")
                    .header(EVENT_ID_HEADER, eventId)
                    .header(SIGNATURE_HEADER, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            return status >= 200 && status < 300
                    ? DeliveryResult.success(status)
                    : DeliveryResult.failure(status, "Endpoint responded with HTTP " + status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DeliveryResult.failure(null, "Delivery interrupted");
        } catch (InvalidRequestException e) {
            return DeliveryResult.failure(null, e.getMessage());
        } catch (IOException | RuntimeException e) {
            return DeliveryResult.failure(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
