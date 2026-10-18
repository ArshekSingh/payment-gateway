package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.config.GatewayProperties;
import com.arshek.paymentgateway.exception.InvalidRequestException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * Guards against SSRF: merchants control the webhook URL, so the gateway must not be usable to
 * reach internal services. Runs at configuration time and again right before every delivery.
 * Note: a DNS-rebinding attacker can still race the check; pin the resolved address in the HTTP
 * client if that is part of your threat model.
 */
@Component
public class WebhookUrlValidator {

    private final boolean allowPrivateTargets;

    public WebhookUrlValidator(GatewayProperties properties) {
        this.allowPrivateTargets = properties.webhook().allowPrivateTargets();
    }

    public void validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw invalid("Webhook URL is not a valid URI");
        }
        String scheme = uri.getScheme();
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean http = "http".equalsIgnoreCase(scheme);
        if (!https && !(http && allowPrivateTargets)) {
            throw invalid("Webhook URL must use https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw invalid("Webhook URL must have a host and must not embed credentials");
        }
        if (allowPrivateTargets) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isInternal(address)) {
                    throw invalid("Webhook URL must not point to a private or loopback address");
                }
            }
        } catch (UnknownHostException e) {
            throw invalid("Webhook URL host could not be resolved");
        }
    }

    private static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] raw = a.getAddress();
        // IPv6 unique local addresses fc00::/7 are not covered by isSiteLocalAddress()
        return raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
    }

    private static InvalidRequestException invalid(String message) {
        return new InvalidRequestException("invalid_webhook_url", message);
    }
}
