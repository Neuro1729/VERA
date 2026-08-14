package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.persistence.UsageRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class GrantRuntime {
    private final UsageRepository usageStore;
    private final RateLimitService rateLimitService;
    private final Clock clock;

    GrantRuntime(UsageRepository usageStore, RateLimitService rateLimitService, Clock clock) {
        this.usageStore = usageStore;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
    }

    ResourceDistributionResult.RuntimeState of(String tenantId, EntitlementGrant grant) {
        Instant now = Instant.now(clock);
        return switch (grant.value()) {
            case QuotaValue quota -> quotaRuntime(tenantId, grant.id(), quota, now);
            case BooleanValue booleanValue -> new ResourceDistributionResult.BooleanRuntime(booleanValue.value());
            case QuantityValue quantity -> new ResourceDistributionResult.QuantityRuntime(quantity.value(), quantity.unit());
            case RangeValue range -> new ResourceDistributionResult.RangeRuntime(range.min(), range.max(), range.unit());
            case TimeRangeValue timeRange -> timeRangeRuntime(timeRange, now);
            case SetValue setValue -> new ResourceDistributionResult.SetRuntime(setValue.values());
            case TextValue textValue -> new ResourceDistributionResult.TextRuntime(textValue.value());
            case RateLimitValue rateLimit -> rateLimitRuntime(tenantId, grant.id(), rateLimit);
        };
    }

    static boolean isCurrentlyActive(ResourceDistributionResult.RuntimeState runtime) {
        return switch (runtime) {
            case ResourceDistributionResult.TimeRangeRuntime timeRange -> timeRange.active();
            case ResourceDistributionResult.BooleanRuntime booleanRuntime -> booleanRuntime.value();
            case ResourceDistributionResult.QuotaRuntime quota -> quota.remaining().signum() > 0;
            case ResourceDistributionResult.RateLimitRuntime rateLimit -> rateLimit.availableTokens().signum() > 0;
            case ResourceDistributionResult.QuantityRuntime ignored -> true;
            case ResourceDistributionResult.RangeRuntime ignored -> true;
            case ResourceDistributionResult.SetRuntime ignored -> true;
            case ResourceDistributionResult.TextRuntime ignored -> true;
        };
    }

    private ResourceDistributionResult.QuotaRuntime quotaRuntime(
            String tenantId, String grantId, QuotaValue quota, Instant now) {
        QuotaWindow window = QuotaWindow.forInstant(now, quota.period());
        Usage usage = usageStore.get(tenantId, grantId);
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
        return new ResourceDistributionResult.QuotaRuntime(
                quota.limit(),
                quota.unit(),
                quota.period(),
                consumed,
                quota.limit().subtract(consumed),
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
        return new ResourceDistributionResult.RateLimitRuntime(
                rateLimit.capacity(),
                rateLimit.refillTokens(),
                rateLimit.refillPeriod(),
                rateLimitService.peekAvailableTokens(tenantId, grantId, rateLimit));
    }
}
