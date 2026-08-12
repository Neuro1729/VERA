package com.example.entitlements.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record QuotaValue(BigDecimal limit, String unit, QuotaPeriod period) implements EntitlementValue {
    public QuotaValue {
        Objects.requireNonNull(limit, "quota limit is required");
        Objects.requireNonNull(period, "quota period is required");
        if (limit.signum() < 0) throw new IllegalArgumentException("quota limit cannot be negative");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("quota unit is required");
    }

    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.QUOTA;
    }
}
