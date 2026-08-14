package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.request.RateLimitRequest;
import com.example.entitlements.request.RateLimitResult;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageHistoryStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {
    private static final int REFILL_SCALE = 18;

    private final TenantRegistry tenantRegistry;
    private final EntitlementResolver entitlementResolver;
    private final UsageHistoryStore historyStore;
    private final Clock clock;
    private final ConcurrentMap<String, RateLimitState> buckets = new ConcurrentHashMap<>();

    public RateLimitService(
            TenantRegistry tenantRegistry,
            EntitlementResolver entitlementResolver,
            UsageHistoryStore historyStore,
            Clock clock
    ) {
        this.tenantRegistry = tenantRegistry;
        this.entitlementResolver = entitlementResolver;
        this.historyStore = historyStore;
        this.clock = clock;
    }

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

        String key = bucketKey(tenant.getId(), resolved.grant().id());
        Instant now = Instant.now(clock);
        RateLimitState state = buckets.computeIfAbsent(
                key, ignored -> new RateLimitState(config.capacity(), now));

        RateLimitResult result;
        synchronized (state) {
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
            result = new RateLimitResult(
                    true,
                    "consumed",
                    resolved.grant().id(),
                    resolved.source(),
                    request.tokens(),
                    state.getAvailableTokens());
        }

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
        String key = bucketKey(tenant.getId(), resolved.grant().id());
        Instant now = Instant.now(clock);
        RateLimitState state = buckets.computeIfAbsent(
                key, ignored -> new RateLimitState(config.capacity(), now));
        synchronized (state) {
            refill(state, config, Instant.now(clock));
            return state.getAvailableTokens();
        }
    }

    public void removeBucket(String tenantId, String grantId) {
        if (tenantId == null || tenantId.isBlank() || grantId == null || grantId.isBlank()) {
            return;
        }
        buckets.remove(bucketKey(tenantId, grantId));
    }

    /**
     * Read-only view of current available tokens for distribution.
     * Does not create a bucket when none exists (reports full capacity).
     * If a bucket exists, refills it to reflect current time before reading.
     */
    public BigDecimal peekAvailableTokens(String tenantId, String grantId, RateLimitValue config) {
        Objects.requireNonNull(config, "rate limit config is required");
        if (tenantId == null || tenantId.isBlank() || grantId == null || grantId.isBlank()) {
            return config.capacity();
        }
        RateLimitState state = buckets.get(bucketKey(tenantId, grantId));
        if (state == null) {
            return config.capacity();
        }
        synchronized (state) {
            refill(state, config, Instant.now(clock));
            return state.getAvailableTokens();
        }
    }

    public void clear() {
        buckets.clear();
    }

    /** Visible for tests. */
    public boolean hasBucket(String tenantId, String grantId) {
        return buckets.containsKey(bucketKey(tenantId, grantId));
    }

    /** Visible for tests. */
    public int bucketCount() {
        return buckets.size();
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

    private String bucketKey(String tenantId, String grantId) {
        return tenantId + ":" + grantId;
    }
}
