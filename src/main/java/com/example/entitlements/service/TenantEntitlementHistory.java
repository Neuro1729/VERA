package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementHistoryEvent;

import java.util.List;

public record TenantEntitlementHistory(List<EntitlementHistoryEvent> changes) {
    public TenantEntitlementHistory {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
