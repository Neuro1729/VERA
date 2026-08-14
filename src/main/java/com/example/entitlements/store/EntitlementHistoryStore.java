package com.example.entitlements.store;

import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.persistence.EntitlementHistoryRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EntitlementHistoryStore implements EntitlementHistoryRepository {
    private record ResourceHistoryKey(String tenantId, String resourceId) {}

    private final ConcurrentMap<ResourceHistoryKey, List<EntitlementHistoryEvent>> eventsByResource = new ConcurrentHashMap<>();

    @Override
    public void append(EntitlementHistoryEvent event) {
        Objects.requireNonNull(event, "event is required");
        ResourceHistoryKey key = new ResourceHistoryKey(event.tenantId(), event.resourceId());
        List<EntitlementHistoryEvent> events = eventsByResource.computeIfAbsent(
                key, ignored -> Collections.synchronizedList(new ArrayList<>()));
        events.add(event);
    }

    @Override
    public List<EntitlementHistoryEvent> findByResource(String tenantId, String resourceId) {
        List<EntitlementHistoryEvent> events = eventsByResource.get(new ResourceHistoryKey(tenantId, resourceId));
        if (events == null) return List.of();
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    @Override
    public List<EntitlementHistoryEvent> findByTenant(String tenantId) {
        List<EntitlementHistoryEvent> all = new ArrayList<>();
        for (var entry : eventsByResource.entrySet()) {
            if (!entry.getKey().tenantId().equals(tenantId)) continue;
            List<EntitlementHistoryEvent> events = entry.getValue();
            synchronized (events) {
                all.addAll(events);
            }
        }
        all.sort(Comparator.comparing(EntitlementHistoryEvent::changedAt).thenComparing(EntitlementHistoryEvent::id));
        return List.copyOf(all);
    }

    @Override
    public void clear() {
        eventsByResource.clear();
    }
}
