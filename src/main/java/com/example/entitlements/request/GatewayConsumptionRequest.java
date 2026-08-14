package com.example.entitlements.request;

import java.math.BigDecimal;

public record GatewayConsumptionRequest(
        String subjectId,
        String resourceId,
        String entitlementKey,
        BigDecimal amount
) {}
