package com.example.entitlements.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record QuantityValue(BigDecimal value, String unit) implements EntitlementValue {
    public QuantityValue {
        Objects.requireNonNull(value, "quantity value is required");
        if (value.signum() < 0) throw new IllegalArgumentException("quantity cannot be negative");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("quantity unit is required");
    }

    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.QUANTITY;
    }
}
