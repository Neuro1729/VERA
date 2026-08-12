package com.example.entitlements.domain;

import java.util.Objects;

public record EntitlementGrant(
        String id,
        Target target,
        String resourceId,
        String entitlementKey,
        EntitlementValue value
) {
    public EntitlementGrant {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("grant id is required");
        Objects.requireNonNull(target, "grant target is required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (entitlementKey == null || entitlementKey.isBlank()) throw new IllegalArgumentException("entitlementKey is required");
        Objects.requireNonNull(value, "grant value is required");
    }
}
