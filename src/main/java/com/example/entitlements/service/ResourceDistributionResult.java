package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceDistributionResult(
        String resourceId,
        String resourceName,
        String scopeId,
        String scopeName,
        List<EntitlementDistribution> entitlements
) {
    public ResourceDistributionResult {
        entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
    }

    public record EntitlementDistribution(
            String entitlementKey,
            String entitlementName,
            EntitlementValueType valueType,
            List<GrantDistribution> grants
    ) {
        public EntitlementDistribution {
            grants = grants == null ? List.of() : List.copyOf(grants);
        }
    }

    public record GrantDistribution(
            String grantId,
            Target source,
            EntitlementValue value,
            RuntimeState runtime,
            List<Child> children
    ) {
        public GrantDistribution {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    public record Child(
            String id,
            TargetType type,
            String kind,
            String name
    ) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = QuotaRuntime.class, name = "QUOTA"),
            @JsonSubTypes.Type(value = BooleanRuntime.class, name = "BOOLEAN"),
            @JsonSubTypes.Type(value = QuantityRuntime.class, name = "QUANTITY"),
            @JsonSubTypes.Type(value = RangeRuntime.class, name = "RANGE"),
            @JsonSubTypes.Type(value = TimeRangeRuntime.class, name = "TIME_RANGE"),
            @JsonSubTypes.Type(value = SetRuntime.class, name = "SET"),
            @JsonSubTypes.Type(value = TextRuntime.class, name = "TEXT"),
            @JsonSubTypes.Type(value = RateLimitRuntime.class, name = "RATE_LIMIT")
    })
    public sealed interface RuntimeState permits
            QuotaRuntime,
            BooleanRuntime,
            QuantityRuntime,
            RangeRuntime,
            TimeRangeRuntime,
            SetRuntime,
            TextRuntime,
            RateLimitRuntime {}

    public record QuotaRuntime(
            BigDecimal limit,
            String unit,
            QuotaPeriod period,
            BigDecimal consumed,
            BigDecimal remaining,
            Instant periodStart,
            Instant periodEnd
    ) implements RuntimeState {}

    public record BooleanRuntime(boolean value) implements RuntimeState {}

    public record QuantityRuntime(BigDecimal value, String unit) implements RuntimeState {}

    public record RangeRuntime(BigDecimal min, BigDecimal max, String unit) implements RuntimeState {}

    public record TimeRangeRuntime(
            Instant from,
            Instant until,
            boolean active,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Duration timeRemaining
    ) implements RuntimeState {}

    public record SetRuntime(Set<String> values) implements RuntimeState {
        public SetRuntime {
            values = values == null ? Set.of() : Set.copyOf(values);
        }
    }

    public record TextRuntime(String value) implements RuntimeState {}

    public record RateLimitRuntime(
            BigDecimal capacity,
            BigDecimal refillTokens,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Duration refillPeriod,
            BigDecimal availableTokens
    ) implements RuntimeState {}
}
