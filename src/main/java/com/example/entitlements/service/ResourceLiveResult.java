package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.EntitlementValueType;
import com.example.entitlements.domain.Target;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceLiveResult(
        String resourceId,
        String resourceName,
        Instant observedAt,
        List<EntitlementLive> entitlements
) {
    public ResourceLiveResult {
        entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
    }

    public record EntitlementLive(
            String entitlementKey,
            String entitlementName,
            EntitlementValueType valueType,
            List<GrantLive> grants
    ) {
        public EntitlementLive {
            grants = grants == null ? List.of() : List.copyOf(grants);
        }
    }

    public record GrantLive(
            String grantId,
            Target source,
            EntitlementValue value,
            ResourceDistributionResult.RuntimeState runtime,
            int entitledSubjectCount,
            boolean active
    ) {}
}
