package com.example.entitlements.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record UsageBucket(
        String tenantId,
        String subjectId,
        String subjectNameAtTime,
        String resourceId,
        String resourceNameAtTime,
        String resourceKindAtTime,
        String entitlementKey,
        String grantId,
        Target grantTarget,
        String grantTargetNameAtTime,
        Instant bucketStart,
        Instant bucketEnd,
        BigDecimal totalConsumed,
        long operationCount,
        Instant firstOccurredAt,
        Instant lastOccurredAt
) {
    public UsageBucket {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId is required");
        if (subjectNameAtTime == null || subjectNameAtTime.isBlank()) subjectNameAtTime = subjectId;
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (entitlementKey == null || entitlementKey.isBlank()) throw new IllegalArgumentException("entitlementKey is required");
        if (grantId == null || grantId.isBlank()) throw new IllegalArgumentException("grantId is required");
        Objects.requireNonNull(grantTarget, "grantTarget is required");
        if (grantTargetNameAtTime == null || grantTargetNameAtTime.isBlank()) grantTargetNameAtTime = grantTarget.id();
        Objects.requireNonNull(bucketStart, "bucketStart is required");
        Objects.requireNonNull(bucketEnd, "bucketEnd is required");
        Objects.requireNonNull(totalConsumed, "totalConsumed is required");
        if (operationCount < 0) throw new IllegalArgumentException("operationCount cannot be negative");
        Objects.requireNonNull(firstOccurredAt, "firstOccurredAt is required");
        Objects.requireNonNull(lastOccurredAt, "lastOccurredAt is required");
    }
}
