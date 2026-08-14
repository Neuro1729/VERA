package com.example.entitlements.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

public record UsageEvent(
        String id,
        String tenantId,
        String resourceId,
        String resourceNameAtTime,
        String resourceKindAtTime,
        String entitlementKey,
        String grantId,
        Target grantTarget,
        String grantTargetNameAtTime,
        String subjectId,
        String subjectNameAtTime,
        JsonNode usedValue,
        Instant occurredAt
) {
    public UsageEvent {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("usage event id is required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (entitlementKey == null || entitlementKey.isBlank()) throw new IllegalArgumentException("entitlementKey is required");
        if (grantId == null || grantId.isBlank()) throw new IllegalArgumentException("grantId is required");
        Objects.requireNonNull(grantTarget, "grantTarget is required");
        if (grantTargetNameAtTime == null || grantTargetNameAtTime.isBlank()) grantTargetNameAtTime = grantTarget.id();
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId is required");
        if (subjectNameAtTime == null || subjectNameAtTime.isBlank()) subjectNameAtTime = subjectId;
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (usedValue != null) usedValue = usedValue.deepCopy();
    }
}
