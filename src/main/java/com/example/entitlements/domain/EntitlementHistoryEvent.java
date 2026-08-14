package com.example.entitlements.domain;

import java.time.Instant;
import java.util.Objects;

public record EntitlementHistoryEvent(
        String id,
        String tenantId,
        String resourceId,
        String entitlementKey,
        Target target,
        EntitlementChangeType changeType,
        String previousGrantId,
        String newGrantId,
        EntitlementValue oldValue,
        EntitlementValue newValue,
        Instant changedAt
) {
    public EntitlementHistoryEvent {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("history event id is required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (entitlementKey == null || entitlementKey.isBlank()) throw new IllegalArgumentException("entitlementKey is required");
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(changeType, "changeType is required");
        Objects.requireNonNull(changedAt, "changedAt is required");
    }
}
