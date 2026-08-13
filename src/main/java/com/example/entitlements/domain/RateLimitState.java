package com.example.entitlements.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class RateLimitState {
    private BigDecimal availableTokens;
    private Instant lastRefillTime;

    public RateLimitState(BigDecimal availableTokens, Instant lastRefillTime) {
        this.availableTokens = Objects.requireNonNull(availableTokens, "availableTokens is required");
        this.lastRefillTime = Objects.requireNonNull(lastRefillTime, "lastRefillTime is required");
    }

    public BigDecimal getAvailableTokens() {
        return availableTokens;
    }

    public Instant getLastRefillTime() {
        return lastRefillTime;
    }

    public void setAvailableTokens(BigDecimal availableTokens) {
        this.availableTokens = Objects.requireNonNull(availableTokens, "availableTokens is required");
    }

    public void setLastRefillTime(Instant lastRefillTime) {
        this.lastRefillTime = Objects.requireNonNull(lastRefillTime, "lastRefillTime is required");
    }
}
