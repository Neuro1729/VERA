package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.persistence.RateLimitStateRepository;
import com.example.entitlements.persistence.UsageHistoryRepository;
import com.example.entitlements.persistence.memory.InMemoryRateLimitStateRepository;
import com.example.entitlements.request.RateLimitRequest;
import com.example.entitlements.request.RateLimitResult;
import com.example.entitlements.store.TenantRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class RateLimitService {
    private static final int REFILL_SCALE = 18;

    private final TenantRegistry tenantRegistry;
    private final EntitlementResolver entitlementResolver;
    private final UsageHistoryRepository historyStore;
    private final Clock clock;
    private final RateLimitStateRepository buckets;

    public RateLimitService(
            TenantRegistry tenantRegistry,
            EntitlementResolver entitlementResolver,
            UsageHistoryRepository historyStore,
            Clock clock
    ) {
        this(tenantRegistry, entitlementResolver, historyStore, clock, new InMemoryRateLimitStateRepository());
    }

    @Autowired
    public RateLimitService(
            TenantRegistry tenantRegistry,
            EntitlementResolver entitlementResolver,
            UsageHistoryRepository historyStore,
            Clock clock,
            RateLimitStateRepository buckets
    ) {
        this.tenantRegistry = tenantRegistry;
        this.entitlementResolver = entitlementResolver;
        this.historyStore = historyStore;
        this.clock = clock;
        this.buckets = buckets;
    }

    @Transactional
    public RateLimitResult tryConsume(RateLimitRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (request.tokens() == null || request.tokens().signum() <= 0) {
            throw new IllegalArgumentException("tokens must be positive");
        }

        Tenant tenant = tenantRegistry.getRequired(request.tenantId());
        if (!tenant.getSubjects().containsKey(request.subjectId())) {
            throw new NoSuchElementException("subject not found: " + request.subjectId());
        }

        ResolvedEntitlement resolved = entitlementResolver.resolve(
                        tenant, request.subjectId(), request.resourceId(), request.entitlementKey())
                .orElseThrow(() -> new NoSuchElementException("no entitlement found"));

        if (!(resolved.grant().value() instanceof RateLimitValue config)) {
            throw new IllegalArgumentException("only RATE_LIMIT entitlements support token bucket consume");
        }

        Instant now = Instant.now(clock);
        buckets.insertIfAbsent(tenant.getId(), resolved.grant().id(), new RateLimitState(config.capacity(), now));
        RateLimitState state = buckets.lock(tenant.getId(), resolved.grant().id());
        if (state == null) {
            state = new RateLimitState(config.capacity(), now);
        }

        refill(state, config, Instant.now(clock));
        if (state.getAvailableTokens().compareTo(request.tokens()) < 0) {
            return new RateLimitResult(
                    false,
                    "rate limit exceeded",
                    resolved.grant().id(),
                    resolved.source(),
                    request.tokens(),
                    state.getAvailableTokens());
        }
        state.setAvailableTokens(state.getAvailableTokens().subtract(request.tokens()));
        buckets.save(tenant.getId(), resolved.grant().id(), state);
        RateLimitResult result = new RateLimitResult(
                true,
                "consumed",
                resolved.grant().id(),
                resolved.source(),
                request.tokens(),
                state.getAvailableTokens());

        Resource resource = tenant.getResources().get(request.resourceId());
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
                request.tokens(),
                Instant.now(clock));
        return result;
    }

    public BigDecimal availableTokens(
            String tenantId,
            String subjectId,
            String resourceId,
            String entitlementKey
    ) {
        Tenant tenant = tenantRegistry.getRequired(tenantId);
        ResolvedEntitlement resolved = entitlementResolver.resolve(tenant, subjectId, resourceId, entitlementKey)
                .orElseThrow(() -> new NoSuchElementException("no entitlement found"));
        if (!(resolved.grant().value() instanceof RateLimitValue config)) {
            throw new IllegalArgumentException("only RATE_LIMIT entitlements support token availability");
        }
        return peekAvailableTokens(tenant.getId(), resolved.grant().id(), config);
    }

    public void removeBucket(String tenantId, String grantId) {
        if (tenantId == null || tenantId.isBlank() || grantId == null || grantId.isBlank()) {
            return;
        }
        buckets.remove(tenantId, grantId);
    }

    /**
     * Read-only view of current available tokens for distribution.
     * Does not create a bucket when none exists (reports full capacity).
     * Computes refill against a copy so evaluate/live queries do not persist state.
     */
    public BigDecimal peekAvailableTokens(String tenantId, String grantId, RateLimitValue config) {
        Objects.requireNonNull(config, "rate limit config is required");
        if (tenantId == null || tenantId.isBlank() || grantId == null || grantId.isBlank()) {
            return config.capacity();
        }
        RateLimitState stored = buckets.get(tenantId, grantId);
        if (stored == null) {
            return config.capacity();
        }
        RateLimitState state = new RateLimitState(stored.getAvailableTokens(), stored.getLastRefillTime());
        refill(state, config, Instant.now(clock));
        return state.getAvailableTokens();
    }

    public void clear() {
        buckets.clear();
    }

    /** Visible for tests. */
    public boolean hasBucket(String tenantId, String grantId) {
        return buckets.exists(tenantId, grantId);
    }

    /** Visible for tests. */
    public int bucketCount() {
        return buckets.count();
    }

    private void refill(RateLimitState state, RateLimitValue config, Instant now) {
        Instant last = state.getLastRefillTime();
        if (!now.isAfter(last)) {
            state.setLastRefillTime(now);
            return;
        }

        Duration elapsed = Duration.between(last, now);
        long periodNanos = config.refillPeriod().toNanos();
        if (periodNanos <= 0) {
            throw new IllegalStateException("invalid refillPeriod");
        }

        BigDecimal generated = BigDecimal.valueOf(elapsed.toNanos())
                .multiply(config.refillTokens())
                .divide(BigDecimal.valueOf(periodNanos), REFILL_SCALE, RoundingMode.HALF_UP);

        BigDecimal refilled = state.getAvailableTokens().add(generated);
        if (refilled.compareTo(config.capacity()) > 0) {
            refilled = config.capacity();
        }
        state.setAvailableTokens(refilled);
        state.setLastRefillTime(now);
    }
}
