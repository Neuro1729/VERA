package com.example.entitlements.service;

import com.example.entitlements.domain.Target;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ResourceUsageHistory(
        String resourceId,
        String resourceName,
        String resourceKind,
        List<EntitlementUsage> entitlements
) {
    public ResourceUsageHistory {
        entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
    }

    public record EntitlementUsage(
            String entitlementKey,
            List<GrantUsage> grants
    ) {
        public EntitlementUsage {
            grants = grants == null ? List.of() : List.copyOf(grants);
        }
    }

    public record GrantUsage(
            String grantId,
            Target grantTarget,
            String grantTargetNameAtTime,
            List<UsageRecord> usage
    ) {
        public GrantUsage {
            usage = usage == null ? List.of() : List.copyOf(usage);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = BucketUsage.class, name = "BUCKET"),
            @JsonSubTypes.Type(value = EventUsage.class, name = "EVENT")
    })
    public sealed interface UsageRecord permits BucketUsage, EventUsage {}

    public record BucketUsage(
            String subjectId,
            String subjectNameAtTime,
            Instant bucketStart,
            Instant bucketEnd,
            BigDecimal totalConsumed,
            long operationCount,
            Instant firstOccurredAt,
            Instant lastOccurredAt
    ) implements UsageRecord {}

    public record EventUsage(
            String subjectId,
            String subjectNameAtTime,
            Instant occurredAt,
            JsonNode usedValue
    ) implements UsageRecord {}
}
