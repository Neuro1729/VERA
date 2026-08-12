package com.example.entitlements.request;

import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.Target;

public record GrantInput(
        String id,
        Target target,
        String resourceId,
        String entitlementKey,
        EntitlementValue value
) {}
