package com.example.entitlements.store;

import com.example.entitlements.domain.EntitlementChangeType;
import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.domain.QuotaPeriod;
import com.example.entitlements.domain.QuotaValue;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementHistoryStoreTest {
    private EntitlementHistoryStore store;

    @BeforeEach
    void setUp() {
        store = new EntitlementHistoryStore();
    }

    @Test
    void groupsByTenantAndResourceAndPreservesAppendOrder() {
        EntitlementHistoryEvent first = event("acme", "gpu", "gpu.hours", "e1");
        EntitlementHistoryEvent second = event("acme", "gpu", "gpu.enabled", "e2");
        store.append(first);
        store.append(second);

        assertEquals(List.of(first, second), store.findByResource("acme", "gpu"));
    }

    @Test
    void isolatesTenantsThatShareAResourceId() {
        store.append(event("acme", "gpu", "gpu.hours", "acme-1"));
        store.append(event("globex", "gpu", "gpu.hours", "globex-1"));

        assertEquals(List.of("acme-1"), ids("acme", "gpu"));
        assertEquals(List.of("globex-1"), ids("globex", "gpu"));
    }

    @Test
    void isolatesResourcesInTheSameTenant() {
        store.append(event("acme", "gpu", "gpu.hours", "gpu-1"));
        store.append(event("acme", "api", "api.requests", "api-1"));

        assertEquals(List.of("gpu-1"), ids("acme", "gpu"));
        assertEquals(List.of("api-1"), ids("acme", "api"));
    }

    @Test
    void unknownResourceReturnsEmptyList() {
        assertTrue(store.findByResource("acme", "missing").isEmpty());
    }

    @Test
    void clearRemovesAllHistory() {
        store.append(event("acme", "gpu", "gpu.hours", "e1"));
        store.clear();
        assertTrue(store.findByResource("acme", "gpu").isEmpty());
    }

    private List<String> ids(String tenantId, String resourceId) {
        return store.findByResource(tenantId, resourceId).stream().map(EntitlementHistoryEvent::id).toList();
    }

    private static EntitlementHistoryEvent event(String tenantId, String resourceId, String key, String id) {
        return new EntitlementHistoryEvent(
                id,
                tenantId,
                resourceId,
                key,
                new Target(TargetType.SCOPE, "engineering"),
                EntitlementChangeType.CREATED,
                null,
                "g1",
                null,
                new QuotaValue(new BigDecimal("5000"), "hour", QuotaPeriod.MONTHLY),
                Instant.parse("2026-08-01T10:00:00Z"));
    }
}
