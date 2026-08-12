package com.example.entitlements.request;

import java.math.BigDecimal;

public record ConsumptionRequest(
        String tenantId,
        String subjectId,
        String resourceId,
        String entitlementKey,
        BigDecimal amount
) {}
