package com.example.entitlements.request;

import com.example.entitlements.domain.Target;

import java.math.BigDecimal;

public record RateLimitResult(
        boolean allowed,
        String reason,
        String grantId,
        Target source,
        BigDecimal requestedTokens,
        BigDecimal availableTokens
) {}
