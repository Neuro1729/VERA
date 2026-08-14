package com.example.entitlements.service;

import java.util.List;

public record TenantUsageHistory(List<ResourceUsageHistory> resources) {
    public TenantUsageHistory {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
