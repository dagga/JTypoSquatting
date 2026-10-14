package com.aleph.graymatter.jtyposquatting.abuse;

import java.time.Duration;

/**
 * Thrown when a user exceeds a usage quota. Mapped to HTTP 429.
 */
public class QuotaExceededException extends RuntimeException {

    private final Duration retryAfter;

    public QuotaExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
