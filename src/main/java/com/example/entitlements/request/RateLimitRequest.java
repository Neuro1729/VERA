package com.example.entitlements.request;

import java.math.BigDecimal;

public record RateLimitRequest(
        String tenantId,
        String subjectId,
        String resourceId,
        String entitlementKey,
        BigDecimal tokens
) {}
