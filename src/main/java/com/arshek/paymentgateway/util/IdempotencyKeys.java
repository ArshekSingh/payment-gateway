package com.arshek.paymentgateway.util;

import com.arshek.paymentgateway.exception.InvalidRequestException;

public final class IdempotencyKeys {

    public static final int MAX_LENGTH = 100;

    private IdempotencyKeys() {
    }

    /** Returns the trimmed key, or null when absent/blank. */
    public static String normalize(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidRequestException("invalid_idempotency_key",
                    "Idempotency-Key must be at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
