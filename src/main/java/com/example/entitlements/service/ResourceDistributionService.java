package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.persistence.UsageRepository;
import com.example.entitlements.store.TenantRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class ResourceDistributionService {
    private final TenantRegistry registry;
    private final GrantRuntime grantRuntime;
    private final Clock clock;

    public ResourceDistributionService(
            TenantRegistry registry,
            UsageRepository usageStore,
            RateLimitService rateLimitService,
            Clock clock
    ) {
        this.registry = registry;
        this.grantRuntime = new GrantRuntime(usageStore, rateLimitService, clock);
        this.clock = clock;
    }

    public ResourceDistributionResult distribute(String tenantId, String resourceId, String scopeId) {
        Tenant tenant = registry.getRequired(tenantId);
        synchronized (tenant) {
            Resource resource = tenant.getResources().get(resourceId);
            if (resource == null) throw new NoSuchElementException("resource not found: " + resourceId);
            Scope chosen = tenant.getScopes().get(scopeId);
            if (chosen == null) throw new NoSuchElementException("scope not found: " + scopeId);

            Map<String, EntitlementGrant> chosenEffective = resolveChosenScopeEffective(tenant, chosen, resource);
            List<ChildRef> children = immediateChildren(tenant, chosen);

            List<ResourceDistributionResult.EntitlementDistribution> entitlements = new ArrayList<>();
            for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
                entitlements.add(buildEntitlementDistribution(
                        tenant, resourceId, definition, chosenEffective.get(definition.key()), children));
            }

            return new ResourceDistributionResult(
                    resource.id(),
                    resource.name(),
                    chosen.getId(),
                    chosen.getName(),
                    entitlements);
        }
    }

    /**
     * Resource-wide current snapshot: every grant on the resource, current runtime,
     * and how many subjects currently resolve to that grant. Read-only.
     */
    public ResourceLiveResult live(String tenantId, String resourceId) {
        Tenant tenant = registry.getRequired(tenantId);
        synchronized (tenant) {
            Resource resource = tenant.getResources().get(resourceId);
            if (resource == null) throw new NoSuchElementException("resource not found: " + resourceId);

            Map<String, Integer> entitledByGrantId = new LinkedHashMap<>();
            for (EntitlementGrant grant : tenant.getGrants().values()) {
                if (grant.resourceId().equals(resourceId)) {
                    entitledByGrantId.put(grant.id(), 0);
                }
            }

            if (tenant.getRootScopeId() != null) {
                countEntitledSubjects(
                        tenant,
                        resourceId,
                        resource.entitlementDefinitions(),
                        tenant.getRootScopeId(),
                        new HashMap<>(),
                        entitledByGrantId);
            }

            List<ResourceLiveResult.EntitlementLive> entitlements = new ArrayList<>();
            for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
                List<ResourceLiveResult.GrantLive> grants = new ArrayList<>();
                for (EntitlementGrant grant : tenant.getGrants().values()) {
                    if (!grant.resourceId().equals(resourceId) || !grant.entitlementKey().equals(definition.key())) {
                        continue;
                    }
                    ResourceDistributionResult.RuntimeState runtime = grantRuntime.of(tenant.getId(), grant);
                    grants.add(new ResourceLiveResult.GrantLive(
                            grant.id(),
                            grant.target(),
                            grant.value(),
                            runtime,
                            entitledByGrantId.getOrDefault(grant.id(), 0),
                            GrantRuntime.isCurrentlyActive(runtime)));
                }
                entitlements.add(new ResourceLiveResult.EntitlementLive(
                        definition.key(),
                        definition.name(),
                        definition.valueType(),
                        grants));
            }

            return new ResourceLiveResult(
                    resource.id(),
                    resource.name(),
                    Instant.now(clock),
                    entitlements);
        }
    }

    /**
     * Single ancestor walk for the chosen scope: nearest grant per entitlement key wins.
     * Complexity O(H * K) with indexed exact lookups.
     */
    private Map<String, EntitlementGrant> resolveChosenScopeEffective(
            Tenant tenant,
            Scope chosen,
            Resource resource
    ) {
        Map<String, EntitlementGrant> effective = new LinkedHashMap<>();
        Set<String> unresolved = new LinkedHashSet<>();
        for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
            unresolved.add(definition.key());
        }

        String currentScopeId = chosen.getId();
        while (currentScopeId != null && !unresolved.isEmpty()) {
            Target scopeTarget = new Target(TargetType.SCOPE, currentScopeId);
            Iterator<String> keys = unresolved.iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                Optional<EntitlementGrant> grant = tenant.findGrant(scopeTarget, resource.id(), key);
                if (grant.isPresent()) {
                    effective.put(key, grant.get());
                    keys.remove();
                }
            }
            Scope scope = tenant.getScopes().get(currentScopeId);
            currentScopeId = scope == null ? null : scope.getParentScopeId();
        }
        return effective;
    }

    private List<ChildRef> immediateChildren(Tenant tenant, Scope chosen) {
        List<ChildRef> children = new ArrayList<>();
        for (String childScopeId : chosen.getChildScopeIds()) {
            Scope child = tenant.getScopes().get(childScopeId);
            if (child == null) continue;
            children.add(new ChildRef(
                    child.getId(),
                    TargetType.SCOPE,
                    child.getKind(),
                    child.getName(),
                    new Target(TargetType.SCOPE, child.getId())));
        }
        for (String subjectId : chosen.getSubjectIds()) {
            Subject subject = tenant.getSubjects().get(subjectId);
            if (subject == null) continue;
            children.add(new ChildRef(
                    subject.getId(),
                    TargetType.SUBJECT,
                    subject.getKind(),
                    subject.getName(),
                    new Target(TargetType.SUBJECT, subject.getId())));
        }
        return children;
    }

    private ResourceDistributionResult.EntitlementDistribution buildEntitlementDistribution(
            Tenant tenant,
            String resourceId,
            EntitlementDefinition definition,
            EntitlementGrant chosenEffectiveGrant,
            List<ChildRef> children
    ) {
        Map<String, List<ResourceDistributionResult.Child>> childrenByGrantId = new LinkedHashMap<>();
        Map<String, EntitlementGrant> grantsById = new LinkedHashMap<>();

        for (ChildRef child : children) {
            EntitlementGrant direct = tenant.findGrant(child.target(), resourceId, definition.key()).orElse(null);
            EntitlementGrant winner = direct != null ? direct : chosenEffectiveGrant;
            if (winner == null) continue;

            grantsById.putIfAbsent(winner.id(), winner);
            childrenByGrantId
                    .computeIfAbsent(winner.id(), ignored -> new ArrayList<>())
                    .add(new ResourceDistributionResult.Child(child.id(), child.type(), child.kind(), child.name()));
        }

        List<ResourceDistributionResult.GrantDistribution> grantDistributions = new ArrayList<>();
        for (Map.Entry<String, List<ResourceDistributionResult.Child>> entry : childrenByGrantId.entrySet()) {
            EntitlementGrant grant = grantsById.get(entry.getKey());
            grantDistributions.add(new ResourceDistributionResult.GrantDistribution(
                    grant.id(),
                    grant.target(),
                    grant.value(),
                    grantRuntime.of(tenant.getId(), grant),
                    entry.getValue()));
        }

        return new ResourceDistributionResult.EntitlementDistribution(
                definition.key(),
                definition.name(),
                definition.valueType(),
                grantDistributions);
    }

    /**
     * One tree walk: inherit nearest scope grant, overlay subject direct grants,
     * increment entitled counts. Overlays are restored on backtrack.
     */
    private void countEntitledSubjects(
            Tenant tenant,
            String resourceId,
            List<EntitlementDefinition> definitions,
            String scopeId,
            Map<String, EntitlementGrant> inherited,
            Map<String, Integer> entitledByGrantId
    ) {
        Scope scope = tenant.getScopes().get(scopeId);
        if (scope == null) return;

        List<Overlay> overlays = new ArrayList<>();
        Target scopeTarget = new Target(TargetType.SCOPE, scope.getId());
        for (EntitlementDefinition definition : definitions) {
            Optional<EntitlementGrant> direct = tenant.findGrant(scopeTarget, resourceId, definition.key());
            if (direct.isPresent()) {
                overlays.add(new Overlay(definition.key(), inherited.put(definition.key(), direct.get())));
            }
        }

        for (String subjectId : scope.getSubjectIds()) {
            Subject subject = tenant.getSubjects().get(subjectId);
            if (subject == null) continue;
            Target subjectTarget = new Target(TargetType.SUBJECT, subject.getId());
            for (EntitlementDefinition definition : definitions) {
                EntitlementGrant winner = tenant.findGrant(subjectTarget, resourceId, definition.key())
                        .orElse(inherited.get(definition.key()));
                if (winner != null) {
                    entitledByGrantId.merge(winner.id(), 1, Integer::sum);
                }
            }
        }

        for (String childId : scope.getChildScopeIds()) {
            countEntitledSubjects(tenant, resourceId, definitions, childId, inherited, entitledByGrantId);
        }

        for (Overlay overlay : overlays) {
            if (overlay.previous() == null) {
                inherited.remove(overlay.key());
            } else {
                inherited.put(overlay.key(), overlay.previous());
            }
        }
    }

    private record Overlay(String key, EntitlementGrant previous) {}

    private record ChildRef(String id, TargetType type, String kind, String name, Target target) {}
}
