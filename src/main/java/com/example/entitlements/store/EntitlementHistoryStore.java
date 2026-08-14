package com.example.entitlements.store;

import com.example.entitlements.domain.EntitlementHistoryEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class EntitlementHistoryStore {
    private record ResourceHistoryKey(String tenantId, String resourceId) {}

    private final ConcurrentMap<ResourceHistoryKey, List<EntitlementHistoryEvent>> eventsByResource = new ConcurrentHashMap<>();

    public void append(EntitlementHistoryEvent event) {
        Objects.requireNonNull(event, "event is required");
        ResourceHistoryKey key = new ResourceHistoryKey(event.tenantId(), event.resourceId());
        List<EntitlementHistoryEvent> events = eventsByResource.computeIfAbsent(
                key, ignored -> Collections.synchronizedList(new ArrayList<>()));
        events.add(event);
    }

    public List<EntitlementHistoryEvent> findByResource(String tenantId, String resourceId) {
        List<EntitlementHistoryEvent> events = eventsByResource.get(new ResourceHistoryKey(tenantId, resourceId));
        if (events == null) return List.of();
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public void clear() {
        eventsByResource.clear();
    }
}
