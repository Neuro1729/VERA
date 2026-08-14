package com.example.entitlements.store;

import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.persistence.EntitlementHistoryRepository;

import java.util.ArrayList;
import java.util.Collections;
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
    public void clear() {
        eventsByResource.clear();
    }
}
