package com.example.entitlements.request;

import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.Target;

import java.math.BigDecimal;

public record EvaluationResult(
        boolean allowed,
        String reason,
        String grantId,
        Target source,
        EntitlementValue value,
        BigDecimal remaining
) {}
