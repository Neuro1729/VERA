package com.example.entitlements.cache;

import java.util.Objects;

public record ResolutionKey(
        String tenantId,
        String subjectId,
        String resourceId,
        String entitlementKey
) {
    public ResolutionKey {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(subjectId, "subjectId is required");
        Objects.requireNonNull(resourceId, "resourceId is required");
        Objects.requireNonNull(entitlementKey, "entitlementKey is required");
    }
}
