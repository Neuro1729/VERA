package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ResourceDistributionService {
    private final TenantRegistry registry;
    private final UsageStore usageStore;
    private final RateLimitService rateLimitService;
    private final Clock clock;

    public ResourceDistributionService(
            TenantRegistry registry,
            UsageStore usageStore,
            RateLimitService rateLimitService,
            Clock clock
    ) {
        this.registry = registry;
        this.usageStore = usageStore;
        this.rateLimitService = rateLimitService;
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
                    runtimeState(tenant.getId(), grant),
                    entry.getValue()));
        }

        return new ResourceDistributionResult.EntitlementDistribution(
                definition.key(),
                definition.name(),
                definition.valueType(),
                grantDistributions);
    }

    private ResourceDistributionResult.RuntimeState runtimeState(String tenantId, EntitlementGrant grant) {
        EntitlementValue value = grant.value();
        Instant now = Instant.now(clock);
        return switch (value) {
            case QuotaValue quota -> quotaRuntime(grant.id(), quota, now);
            case BooleanValue booleanValue -> new ResourceDistributionResult.BooleanRuntime(booleanValue.value());
            case QuantityValue quantity -> new ResourceDistributionResult.QuantityRuntime(quantity.value(), quantity.unit());
            case RangeValue range -> new ResourceDistributionResult.RangeRuntime(range.min(), range.max(), range.unit());
            case TimeRangeValue timeRange -> timeRangeRuntime(timeRange, now);
            case SetValue setValue -> new ResourceDistributionResult.SetRuntime(setValue.values());
            case TextValue textValue -> new ResourceDistributionResult.TextRuntime(textValue.value());
            case RateLimitValue rateLimit -> rateLimitRuntime(tenantId, grant.id(), rateLimit);
        };
    }

    /**
     * Read-only quota view: does not create or mutate UsageStore entries.
     */
    private ResourceDistributionResult.QuotaRuntime quotaRuntime(String grantId, QuotaValue quota, Instant now) {
        QuotaWindow window = QuotaWindow.forInstant(now, quota.period());
        Usage usage = usageStore.get(grantId);
        BigDecimal consumed;
        Instant periodStart;
        Instant periodEnd;
        if (usage == null
                || !usage.getPeriodStart().equals(window.start())
                || !usage.getPeriodEnd().equals(window.end())) {
            consumed = BigDecimal.ZERO;
            periodStart = window.start();
            periodEnd = window.end();
        } else {
            consumed = usage.getConsumed();
            periodStart = usage.getPeriodStart();
            periodEnd = usage.getPeriodEnd();
        }
        BigDecimal remaining = quota.limit().subtract(consumed);
        return new ResourceDistributionResult.QuotaRuntime(
                quota.limit(),
                quota.unit(),
                quota.period(),
                consumed,
                remaining,
                periodStart,
                periodEnd);
    }

    private ResourceDistributionResult.TimeRangeRuntime timeRangeRuntime(TimeRangeValue timeRange, Instant now) {
        boolean active = !now.isBefore(timeRange.from()) && now.isBefore(timeRange.until());
        Duration remaining = active ? Duration.between(now, timeRange.until()) : Duration.ZERO;
        return new ResourceDistributionResult.TimeRangeRuntime(
                timeRange.from(),
                timeRange.until(),
                active,
                remaining);
    }

    private ResourceDistributionResult.RateLimitRuntime rateLimitRuntime(
            String tenantId,
            String grantId,
            RateLimitValue rateLimit
    ) {
        BigDecimal available = rateLimitService.peekAvailableTokens(tenantId, grantId, rateLimit);
        return new ResourceDistributionResult.RateLimitRuntime(
                rateLimit.capacity(),
                rateLimit.refillTokens(),
                rateLimit.refillPeriod(),
                available);
    }

    private record ChildRef(String id, TargetType type, String kind, String name, Target target) {}
}
