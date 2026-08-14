package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementChangeType;
import com.example.entitlements.domain.EntitlementDefinition;
import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.persistence.EntitlementHistoryRepository;
import com.example.entitlements.store.TenantRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EntitlementHistoryService {
    private final TenantRegistry registry;
    private final EntitlementHistoryRepository store;
    private final Clock clock;

    public EntitlementHistoryService(TenantRegistry registry, EntitlementHistoryRepository store, Clock clock) {
        this.registry = registry;
        this.store = store;
        this.clock = clock;
    }

    public ResourceEntitlementHistory getHistory(String tenantId, String resourceId) {
        Tenant tenant = registry.getRequired(tenantId);
        synchronized (tenant) {
            Resource resource = tenant.getResources().get(resourceId);
            if (resource == null) throw new NoSuchElementException("resource not found: " + resourceId);

            List<EntitlementHistoryEvent> events = store.findByResource(tenantId, resourceId);
            Map<String, List<EntitlementHistoryEvent>> byKey = new HashMap<>();
            for (EntitlementHistoryEvent event : events) {
                byKey.computeIfAbsent(event.entitlementKey(), ignored -> new ArrayList<>()).add(event);
            }

            List<ResourceEntitlementHistory.EntitlementTimeline> timelines = new ArrayList<>(resource.entitlementDefinitions().size());
            for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
                List<EntitlementHistoryEvent> changes = byKey.get(definition.key());
                timelines.add(new ResourceEntitlementHistory.EntitlementTimeline(
                        definition.key(),
                        definition.name(),
                        definition.valueType(),
                        changes == null ? List.of() : changes));
            }
            return new ResourceEntitlementHistory(resource.id(), resource.name(), timelines);
        }
    }

    public TenantEntitlementHistory getTenantHistory(String tenantId) {
        registry.getRequired(tenantId);
        return new TenantEntitlementHistory(store.findByTenant(tenantId));
    }

    void recordCreated(String tenantId, EntitlementGrant grant) {
        store.append(event(
                tenantId,
                grant,
                EntitlementChangeType.CREATED,
                null,
                grant.id(),
                null,
                grant.value()));
    }

    void recordUpdated(String tenantId, EntitlementGrant previous, EntitlementGrant next) {
        store.append(event(
                tenantId,
                next,
                EntitlementChangeType.UPDATED,
                previous.id(),
                next.id(),
                previous.value(),
                next.value()));
    }

    void recordRemoved(String tenantId, EntitlementGrant grant) {
        store.append(event(
                tenantId,
                grant,
                EntitlementChangeType.REMOVED,
                grant.id(),
                null,
                grant.value(),
                null));
    }

    private EntitlementHistoryEvent event(
            String tenantId,
            EntitlementGrant grant,
            EntitlementChangeType changeType,
            String previousGrantId,
            String newGrantId,
            EntitlementValue oldValue,
            EntitlementValue newValue
    ) {
        return new EntitlementHistoryEvent(
                UUID.randomUUID().toString(),
                tenantId,
                grant.resourceId(),
                grant.entitlementKey(),
                grant.target(),
                changeType,
                previousGrantId,
                newGrantId,
                oldValue,
                newValue,
                Instant.now(clock));
    }
}
