package com.example.entitlements.request;

import java.math.BigDecimal;

public record GatewayRateLimitRequest(
        String subjectId,
        String resourceId,
        String entitlementKey,
        BigDecimal tokens
) {}
