package com.example.entitlements.domain;

import java.time.Instant;
import java.util.Objects;

public record TimeRangeValue(Instant from, Instant until) implements EntitlementValue {
    public TimeRangeValue {
        Objects.requireNonNull(from, "time range start is required");
        Objects.requireNonNull(until, "time range end is required");
        if (!from.isBefore(until)) throw new IllegalArgumentException("time range start must be before end");
    }

    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.TIME_RANGE;
    }
}
