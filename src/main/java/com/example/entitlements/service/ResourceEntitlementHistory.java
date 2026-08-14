package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.domain.EntitlementValueType;

import java.util.List;

public record ResourceEntitlementHistory(
        String resourceId,
        String resourceName,
        List<EntitlementTimeline> entitlements
) {
    public ResourceEntitlementHistory {
        entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
    }

    public record EntitlementTimeline(
            String entitlementKey,
            String entitlementName,
            EntitlementValueType valueType,
            List<EntitlementHistoryEvent> changes
    ) {
        public EntitlementTimeline {
            changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }
}
