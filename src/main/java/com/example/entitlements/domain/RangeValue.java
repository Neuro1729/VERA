package com.example.entitlements.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record RangeValue(BigDecimal min, BigDecimal max, String unit) implements EntitlementValue {
    public RangeValue {
        Objects.requireNonNull(min, "range min is required");
        Objects.requireNonNull(max, "range max is required");
        if (min.compareTo(max) > 0) throw new IllegalArgumentException("range min cannot exceed max");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("range unit is required");
    }

    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.RANGE;
    }
}
