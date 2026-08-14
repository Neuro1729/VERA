package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.persistence.UsageHistoryRepository;
import com.example.entitlements.persistence.UsageRepository;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.ConsumptionResult;
import com.example.entitlements.store.TenantRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;

@Service
public class UsageService {
    private final TenantRegistry registry;
    private final UsageRepository usageStore;
    private final EntitlementResolver resolver;
    private final UsageHistoryRepository historyStore;
    private final Clock clock;

    public UsageService(
            TenantRegistry registry,
            UsageRepository usageStore,
            EntitlementResolver resolver,
            UsageHistoryRepository historyStore,
            Clock clock
    ) {
        this.registry = registry;
        this.usageStore = usageStore;
        this.resolver = resolver;
        this.historyStore = historyStore;
        this.clock = clock;
    }

    @Transactional
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

            Usage usage = currentUsage(tenant.getId(), resolved.grant().id(), quota, Instant.now(clock), true);
            BigDecimal remainingBefore = quota.limit().subtract(usage.getConsumed());
            if (request.amount().compareTo(remainingBefore) > 0) {
                return new ConsumptionResult(false, "quota exceeded", resolved.grant().id(), resolved.source(),
                        request.amount(), usage.getConsumed(), quota.limit(), remainingBefore,
                        usage.getPeriodStart(), usage.getPeriodEnd());
            }

            usage.add(request.amount());
            usageStore.save(tenant.getId(), usage);
            Resource resource = tenant.getResources().get(request.resourceId());
            Instant occurredAt = Instant.now(clock);
            historyStore.addToBucket(
                    tenant.getId(),
                    request.subjectId(),
                    UsageHistorySnapshots.subjectName(tenant, request.subjectId()),
                    resource.id(),
                    resource.name(),
                    resource.kind(),
                    request.entitlementKey(),
                    resolved.grant().id(),
                    resolved.source(),
                    UsageHistorySnapshots.grantTargetName(tenant, resolved.source()),
                    request.amount(),
                    occurredAt);
            BigDecimal remainingAfter = quota.limit().subtract(usage.getConsumed());
            return new ConsumptionResult(true, "consumed", resolved.grant().id(), resolved.source(),
                    request.amount(), usage.getConsumed(), quota.limit(), remainingAfter,
                    usage.getPeriodStart(), usage.getPeriodEnd());
        }
    }

    public BigDecimal remaining(String tenantId, EntitlementGrant grant) {
        if (!(grant.value() instanceof QuotaValue quota)) return null;
        Usage usage = peekUsage(tenantId, grant.id(), quota, Instant.now(clock));
        return quota.limit().subtract(usage.getConsumed());
    }

    public Usage currentUsage(String grantId, QuotaValue quota, Instant now) {
        return currentUsage(null, grantId, quota, now, false);
    }

    Usage currentUsage(String tenantId, String grantId, QuotaValue quota, Instant now, boolean lock) {
        QuotaWindow window = QuotaWindow.forInstant(now, quota.period());
        Usage existing = lock ? usageStore.lock(tenantId, grantId) : usageStore.get(tenantId, grantId);
        if (existing == null) {
            Usage created = new Usage(grantId, BigDecimal.ZERO, window.start(), window.end());
            if (lock) usageStore.save(tenantId, created);
            return created;
        }
        if (!existing.getPeriodStart().equals(window.start()) || !existing.getPeriodEnd().equals(window.end())) {
            existing.reset(window.start(), window.end());
            if (lock) usageStore.save(tenantId, existing);
        }
        return existing;
    }

    private Usage peekUsage(String tenantId, String grantId, QuotaValue quota, Instant now) {
        QuotaWindow window = QuotaWindow.forInstant(now, quota.period());
        Usage existing = usageStore.get(tenantId, grantId);
        if (existing == null
                || !existing.getPeriodStart().equals(window.start())
                || !existing.getPeriodEnd().equals(window.end())) {
            return new Usage(grantId, BigDecimal.ZERO, window.start(), window.end());
        }
        return existing;
    }

    public void removeUsage(String tenantId, String grantId) {
        usageStore.remove(tenantId, grantId);
    }
}
