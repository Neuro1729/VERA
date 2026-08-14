package com.example.entitlements.store;

import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.UsageBucket;
import com.example.entitlements.domain.UsageEvent;
import com.example.entitlements.persistence.UsageHistoryRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UsageHistoryStore implements UsageHistoryRepository {
    private static final Duration BUCKET_SIZE = Duration.ofMinutes(5);

    private record ResourceHistoryKey(String tenantId, String resourceId) {}

    private record UsageBucketKey(
            String tenantId,
            String subjectId,
            String resourceId,
            String entitlementKey,
            String grantId,
            Instant bucketStart
    ) {}

    private final ConcurrentMap<ResourceHistoryKey, List<UsageEvent>> eventsByResource = new ConcurrentHashMap<>();
    private final ConcurrentMap<UsageBucketKey, UsageBucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceHistoryKey, Set<UsageBucketKey>> bucketKeysByResource = new ConcurrentHashMap<>();

    public void appendEvent(UsageEvent event) {
        Objects.requireNonNull(event, "event is required");
        ResourceHistoryKey resourceKey = new ResourceHistoryKey(event.tenantId(), event.resourceId());
        List<UsageEvent> events = eventsByResource.computeIfAbsent(
                resourceKey, ignored -> Collections.synchronizedList(new ArrayList<>()));
        events.add(event);
    }

    public void addToBucket(
            String tenantId,
            String subjectId,
            String subjectNameAtTime,
            String resourceId,
            String resourceNameAtTime,
            String resourceKindAtTime,
            String entitlementKey,
            String grantId,
            Target grantTarget,
            String grantTargetNameAtTime,
            BigDecimal amount,
            Instant occurredAt
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("bucket amount must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Instant bucketStart = bucketStart(occurredAt);
        Instant bucketEnd = bucketEnd(bucketStart);
        UsageBucketKey key = new UsageBucketKey(
                tenantId, subjectId, resourceId, entitlementKey, grantId, bucketStart);

        buckets.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return new UsageBucket(
                        tenantId,
                        subjectId,
                        subjectNameAtTime,
                        resourceId,
                        resourceNameAtTime,
                        resourceKindAtTime,
                        entitlementKey,
                        grantId,
                        grantTarget,
                        grantTargetNameAtTime,
                        bucketStart,
                        bucketEnd,
                        amount,
                        1,
                        occurredAt,
                        occurredAt);
            }
            Instant first = occurredAt.isBefore(existing.firstOccurredAt()) ? occurredAt : existing.firstOccurredAt();
            Instant last = occurredAt.isAfter(existing.lastOccurredAt()) ? occurredAt : existing.lastOccurredAt();
            return new UsageBucket(
                    existing.tenantId(),
                    existing.subjectId(),
                    existing.subjectNameAtTime(),
                    existing.resourceId(),
                    existing.resourceNameAtTime(),
                    existing.resourceKindAtTime(),
                    existing.entitlementKey(),
                    existing.grantId(),
                    existing.grantTarget(),
                    existing.grantTargetNameAtTime(),
                    existing.bucketStart(),
                    existing.bucketEnd(),
                    existing.totalConsumed().add(amount),
                    existing.operationCount() + 1,
                    first,
                    last);
        });

        bucketKeysByResource
                .computeIfAbsent(new ResourceHistoryKey(tenantId, resourceId), ignored -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    public List<UsageEvent> findEventsByResource(String tenantId, String resourceId) {
        List<UsageEvent> events = eventsByResource.get(new ResourceHistoryKey(tenantId, resourceId));
        if (events == null) return List.of();
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public List<UsageBucket> findBucketsByResource(String tenantId, String resourceId) {
        Set<UsageBucketKey> keys = bucketKeysByResource.get(new ResourceHistoryKey(tenantId, resourceId));
        if (keys == null || keys.isEmpty()) return List.of();
        List<UsageBucket> found = new ArrayList<>();
        for (UsageBucketKey key : keys) {
            UsageBucket bucket = buckets.get(key);
            if (bucket != null) found.add(bucket);
        }
        return List.copyOf(found);
    }

    public List<UsageEvent> findEventsByTenant(String tenantId) {
        List<UsageEvent> all = new ArrayList<>();
        for (var entry : eventsByResource.entrySet()) {
            if (!entry.getKey().tenantId().equals(tenantId)) continue;
            List<UsageEvent> events = entry.getValue();
            synchronized (events) {
                all.addAll(events);
            }
        }
        return List.copyOf(all);
    }

    public List<UsageBucket> findBucketsByTenant(String tenantId) {
        List<UsageBucket> found = new ArrayList<>();
        for (var entry : bucketKeysByResource.entrySet()) {
            if (!entry.getKey().tenantId().equals(tenantId)) continue;
            for (UsageBucketKey key : entry.getValue()) {
                UsageBucket bucket = buckets.get(key);
                if (bucket != null) found.add(bucket);
            }
        }
        return List.copyOf(found);
    }

    public boolean hasHistory(String tenantId, String resourceId) {
        ResourceHistoryKey key = new ResourceHistoryKey(tenantId, resourceId);
        List<UsageEvent> events = eventsByResource.get(key);
        if (events != null) {
            synchronized (events) {
                if (!events.isEmpty()) return true;
            }
        }
        Set<UsageBucketKey> keys = bucketKeysByResource.get(key);
        return keys != null && !keys.isEmpty();
    }

    public void clear() {
        eventsByResource.clear();
        buckets.clear();
        bucketKeysByResource.clear();
    }

    public static Instant bucketStart(Instant occurredAt) {
        long epochSeconds = occurredAt.getEpochSecond();
        return Instant.ofEpochSecond((epochSeconds / BUCKET_SIZE.getSeconds()) * BUCKET_SIZE.getSeconds());
    }

    public static Instant bucketEnd(Instant bucketStart) {
        return bucketStart.plus(BUCKET_SIZE);
    }
}
