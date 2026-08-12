package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.ConsumptionResult;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;

@Service
public class UsageService {
    private final TenantRegistry registry;
    private final UsageStore usageStore;
    private final EntitlementResolver resolver;
    private final Clock clock;

    public UsageService(TenantRegistry registry, UsageStore usageStore, EntitlementResolver resolver, Clock clock) {
        this.registry = registry;
        this.usageStore = usageStore;
        this.resolver = resolver;
        this.clock = clock;
    }

    public ConsumptionResult consume(ConsumptionRequest request) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("consumption amount must be positive");
        }

        Tenant tenant = registry.getRequired(request.tenantId());
        synchronized (tenant) {
            ResolvedEntitlement resolved = resolver.resolve(
                            tenant, request.subjectId(), request.resourceId(), request.entitlementKey())
                    .orElseThrow(() -> new NoSuchElementException("no entitlement found"));

            if (!(resolved.grant().value() instanceof QuotaValue quota)) {
                throw new IllegalArgumentException("only QUOTA entitlements are consumable");
            }

            Usage usage = currentUsage(resolved.grant().id(), quota, Instant.now(clock));
            BigDecimal remainingBefore = quota.limit().subtract(usage.getConsumed());
            if (request.amount().compareTo(remainingBefore) > 0) {
                return new ConsumptionResult(false, "quota exceeded", resolved.grant().id(), resolved.source(),
                        request.amount(), usage.getConsumed(), quota.limit(), remainingBefore,
                        usage.getPeriodStart(), usage.getPeriodEnd());
            }

            usage.add(request.amount());
            BigDecimal remainingAfter = quota.limit().subtract(usage.getConsumed());
            return new ConsumptionResult(true, "consumed", resolved.grant().id(), resolved.source(),
                    request.amount(), usage.getConsumed(), quota.limit(), remainingAfter,
                    usage.getPeriodStart(), usage.getPeriodEnd());
        }
    }

    public BigDecimal remaining(EntitlementGrant grant) {
        if (!(grant.value() instanceof QuotaValue quota)) return null;
        Usage usage = currentUsage(grant.id(), quota, Instant.now(clock));
        return quota.limit().subtract(usage.getConsumed());
    }

    public Usage currentUsage(String grantId, QuotaValue quota, Instant now) {
        QuotaWindow window = QuotaWindow.forInstant(now, quota.period());
        Usage existing = usageStore.get(grantId);
        if (existing == null) {
            Usage created = new Usage(grantId, BigDecimal.ZERO, window.start(), window.end());
            usageStore.put(grantId, created);
            return created;
        }
        if (!existing.getPeriodStart().equals(window.start()) || !existing.getPeriodEnd().equals(window.end())) {
            existing.reset(window.start(), window.end());
        }
        return existing;
    }

    public void removeUsage(String grantId) {
        usageStore.remove(grantId);
    }
}
