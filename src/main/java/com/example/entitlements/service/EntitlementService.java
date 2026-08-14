package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.EvaluationResult;
import com.example.entitlements.store.TenantRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class EntitlementService {
    private final TenantRegistry registry;
    private final EntitlementResolver resolver;
    private final UsageService usageService;
    private final RateLimitService rateLimitService;
    private final Clock clock;

    public EntitlementService(
            TenantRegistry registry,
            EntitlementResolver resolver,
            UsageService usageService,
            RateLimitService rateLimitService,
            Clock clock
    ) {
        this.registry = registry;
        this.resolver = resolver;
        this.usageService = usageService;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
    }

    public EvaluationResult evaluate(EvaluationRequest request) {
        Tenant tenant = registry.getRequired(request.tenantId());
        ResolvedEntitlement resolved = resolver.resolve(
                        tenant, request.subjectId(), request.resourceId(), request.entitlementKey())
                .orElseThrow(() -> new NoSuchElementException("no entitlement found"));

        EntitlementValue value = resolved.grant().value();
        JsonNode requested = request.requestedValue();
        boolean allowed;
        String reason;
        BigDecimal remaining = null;

        if (value instanceof BooleanValue booleanValue) {
            allowed = requested == null || requested.isNull()
                    ? booleanValue.value()
                    : requested.isBoolean() && requested.asBoolean() == booleanValue.value() && booleanValue.value();
            reason = allowed ? "boolean entitlement allows access" : "boolean entitlement denies access";
        } else if (value instanceof QuantityValue quantity) {
            allowed = requested == null || requested.isNull() || numeric(requested).compareTo(quantity.value()) <= 0;
            reason = allowed ? "within quantity limit" : "quantity limit exceeded";
        } else if (value instanceof QuotaValue) {
            remaining = usageService.remaining(request.tenantId(), resolved.grant());
            BigDecimal amount = requested == null || requested.isNull() ? BigDecimal.ONE : numeric(requested);
            allowed = amount.signum() >= 0 && amount.compareTo(remaining) <= 0;
            reason = allowed ? "within remaining quota" : "quota exceeded";
        } else if (value instanceof RateLimitValue) {
            remaining = rateLimitService.availableTokens(
                    request.tenantId(), request.subjectId(), request.resourceId(), request.entitlementKey());
            BigDecimal amount = requested == null || requested.isNull() ? BigDecimal.ONE : numeric(requested);
            allowed = amount.signum() >= 0 && amount.compareTo(remaining) <= 0;
            reason = allowed ? "within available rate-limit tokens" : "rate limit exceeded";
        } else if (value instanceof RangeValue range) {
            if (requested == null || requested.isNull()) {
                allowed = true;
            } else {
                BigDecimal amount = numeric(requested);
                allowed = amount.compareTo(range.min()) >= 0 && amount.compareTo(range.max()) <= 0;
            }
            reason = allowed ? "within allowed range" : "outside allowed range";
        } else if (value instanceof TimeRangeValue timeRange) {
            Instant now = Instant.now(clock);
            allowed = !now.isBefore(timeRange.from()) && now.isBefore(timeRange.until());
            reason = allowed ? "time entitlement is active" : "outside allowed time range";
        } else if (value instanceof SetValue setValue) {
            allowed = requested == null || requested.isNull() || setAllows(setValue.values(), requested);
            reason = allowed ? "requested value is allowed" : "requested value is not in allowed set";
        } else if (value instanceof TextValue textValue) {
            allowed = requested == null || requested.isNull() || (requested.isTextual() && textValue.value().equals(requested.asText()));
            reason = allowed ? "text entitlement matches" : "text entitlement does not match";
        } else {
            throw new IllegalStateException("unsupported entitlement value type");
        }

        return new EvaluationResult(allowed, reason, resolved.grant().id(), resolved.source(), value, remaining);
    }

    private BigDecimal numeric(JsonNode node) {
        if (!node.isNumber()) throw new IllegalArgumentException("requestedValue must be numeric");
        return node.decimalValue();
    }

    private boolean setAllows(Set<String> allowed, JsonNode requested) {
        if (requested.isTextual()) return allowed.contains(requested.asText());
        if (requested.isArray()) {
            Set<String> requestedValues = new HashSet<>();
            for (JsonNode node : requested) {
                if (!node.isTextual()) throw new IllegalArgumentException("SET requestedValue array must contain only strings");
                requestedValues.add(node.asText());
            }
            return allowed.containsAll(requestedValues);
        }
        throw new IllegalArgumentException("SET requestedValue must be a string or array of strings");
    }
}
