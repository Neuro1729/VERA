package com.example.entitlements.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record RateLimitValue(
        BigDecimal capacity,
        BigDecimal refillTokens,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Duration refillPeriod
) implements EntitlementValue {
    public RateLimitValue {
        Objects.requireNonNull(capacity, "rate limit capacity is required");
        Objects.requireNonNull(refillTokens, "rate limit refillTokens is required");
        Objects.requireNonNull(refillPeriod, "rate limit refillPeriod is required");
        if (capacity.signum() <= 0) throw new IllegalArgumentException("rate limit capacity must be positive");
        if (refillTokens.signum() <= 0) throw new IllegalArgumentException("rate limit refillTokens must be positive");
        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("rate limit refillPeriod must be positive");
        }
    }

    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.RATE_LIMIT;
    }
}
