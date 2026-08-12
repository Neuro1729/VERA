package com.example.entitlements.request;

import com.example.entitlements.domain.Target;

import java.math.BigDecimal;
import java.time.Instant;

public record ConsumptionResult(
        boolean allowed,
        String reason,
        String grantId,
        Target source,
        BigDecimal requested,
        BigDecimal consumed,
        BigDecimal limit,
        BigDecimal remaining,
        Instant periodStart,
        Instant periodEnd
) {}
