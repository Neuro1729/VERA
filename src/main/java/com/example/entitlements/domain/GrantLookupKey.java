package com.example.entitlements.domain;

import java.util.Objects;

public record GrantLookupKey(
        TargetType targetType,
        String targetId,
        String resourceId,
        String entitlementKey
) {
    public GrantLookupKey {
        Objects.requireNonNull(targetType, "targetType is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (entitlementKey == null || entitlementKey.isBlank()) throw new IllegalArgumentException("entitlementKey is required");
    }

    public static GrantLookupKey from(EntitlementGrant grant) {
        Objects.requireNonNull(grant, "grant is required");
        return new GrantLookupKey(
                grant.target().type(),
                grant.target().id(),
                grant.resourceId(),
                grant.entitlementKey());
    }

    public static GrantLookupKey of(Target target, String resourceId, String entitlementKey) {
        Objects.requireNonNull(target, "target is required");
        return new GrantLookupKey(target.type(), target.id(), resourceId, entitlementKey);
    }
}
