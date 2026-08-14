package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementDefinition;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.domain.UsageBucket;
import com.example.entitlements.domain.UsageEvent;
import com.example.entitlements.persistence.UsageHistoryRepository;
import com.example.entitlements.store.TenantRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class UsageHistoryService {
    private final TenantRegistry registry;
    private final UsageHistoryRepository store;

    public UsageHistoryService(TenantRegistry registry, UsageHistoryRepository store) {
        this.registry = registry;
        this.store = store;
    }

    public ResourceUsageHistory getHistory(String tenantId, String resourceId, Instant from, Instant until) {
        Tenant tenant = registry.getRequired(tenantId);
        List<UsageEvent> events = store.findEventsByResource(tenantId, resourceId);
        List<UsageBucket> buckets = store.findBucketsByResource(tenantId, resourceId);
        Resource resource = tenant.getResources().get(resourceId);

        if (resource == null && events.isEmpty() && buckets.isEmpty()) {
            throw new NoSuchElementException("resource not found: " + resourceId);
        }

        Map<String, Map<String, GrantAccumulator>> grouped = new LinkedHashMap<>();
        for (UsageEvent event : events) {
            if (!eventInRange(event.occurredAt(), from, until)) continue;
            accumulator(grouped, event.entitlementKey(), event.grantId(), event.grantTarget(), event.grantTargetNameAtTime())
                    .add(event.occurredAt(), new ResourceUsageHistory.EventUsage(
                            event.subjectId(),
                            event.subjectNameAtTime(),
                            event.occurredAt(),
                            event.usedValue()));
        }
        for (UsageBucket bucket : buckets) {
            if (!bucketOverlaps(bucket, from, until)) continue;
            accumulator(grouped, bucket.entitlementKey(), bucket.grantId(), bucket.grantTarget(), bucket.grantTargetNameAtTime())
                    .add(bucket.bucketStart(), new ResourceUsageHistory.BucketUsage(
                            bucket.subjectId(),
                            bucket.subjectNameAtTime(),
                            bucket.bucketStart(),
                            bucket.bucketEnd(),
                            bucket.totalConsumed(),
                            bucket.operationCount(),
                            bucket.firstOccurredAt(),
                            bucket.lastOccurredAt()));
        }

        String resourceName;
        String resourceKind;
        if (resource != null) {
            resourceName = resource.name();
            resourceKind = resource.kind();
        } else {
            Snapshot snapshot = snapshotFromHistory(events, buckets);
            resourceName = snapshot.name();
            resourceKind = snapshot.kind();
        }

        return new ResourceUsageHistory(
                resourceId,
                resourceName,
                resourceKind,
                buildEntitlements(resource, grouped));
    }

    private static GrantAccumulator accumulator(
            Map<String, Map<String, GrantAccumulator>> grouped,
            String entitlementKey,
            String grantId,
            Target grantTarget,
            String grantTargetNameAtTime
    ) {
        Map<String, GrantAccumulator> grants = grouped.computeIfAbsent(entitlementKey, ignored -> new LinkedHashMap<>());
        return grants.computeIfAbsent(grantId, ignored -> new GrantAccumulator(grantId, grantTarget, grantTargetNameAtTime));
    }

    private static List<ResourceUsageHistory.EntitlementUsage> buildEntitlements(
            Resource resource,
            Map<String, Map<String, GrantAccumulator>> grouped
    ) {
        List<String> keys = new ArrayList<>();
        if (resource != null) {
            for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
                if (grouped.containsKey(definition.key())) keys.add(definition.key());
            }
        }
        for (String key : grouped.keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }

        List<ResourceUsageHistory.EntitlementUsage> entitlements = new ArrayList<>(keys.size());
        for (String key : keys) {
            Map<String, GrantAccumulator> grants = grouped.get(key);
            if (grants == null) continue;
            List<ResourceUsageHistory.GrantUsage> grantUsages = grants.values().stream()
                    .map(GrantAccumulator::toGrantUsage)
                    .toList();
            entitlements.add(new ResourceUsageHistory.EntitlementUsage(key, grantUsages));
        }
        return entitlements;
    }

    private static boolean eventInRange(Instant occurredAt, Instant from, Instant until) {
        if (from != null && occurredAt.isBefore(from)) return false;
        if (until != null && !occurredAt.isBefore(until)) return false;
        return true;
    }

    private static boolean bucketOverlaps(UsageBucket bucket, Instant from, Instant until) {
        if (from != null && !bucket.bucketEnd().isAfter(from)) return false;
        if (until != null && !bucket.bucketStart().isBefore(until)) return false;
        return true;
    }

    private static Snapshot snapshotFromHistory(List<UsageEvent> events, List<UsageBucket> buckets) {
        Instant latest = Instant.MIN;
        String name = null;
        String kind = null;
        for (UsageEvent event : events) {
            if (event.occurredAt().isAfter(latest) || name == null) {
                latest = event.occurredAt();
                name = event.resourceNameAtTime();
                kind = event.resourceKindAtTime();
            }
        }
        for (UsageBucket bucket : buckets) {
            Instant candidate = bucket.lastOccurredAt();
            if (candidate.isAfter(latest) || name == null) {
                latest = candidate;
                name = bucket.resourceNameAtTime();
                kind = bucket.resourceKindAtTime();
            }
        }
        return new Snapshot(name, kind);
    }

    private record Snapshot(String name, String kind) {}

    private static final class GrantAccumulator {
        private final String grantId;
        private final Target grantTarget;
        private final String grantTargetNameAtTime;
        private final List<TimedRecord> records = new ArrayList<>();

        private GrantAccumulator(String grantId, Target grantTarget, String grantTargetNameAtTime) {
            this.grantId = grantId;
            this.grantTarget = grantTarget;
            this.grantTargetNameAtTime = grantTargetNameAtTime;
        }

        private void add(Instant time, ResourceUsageHistory.UsageRecord record) {
            records.add(new TimedRecord(time, record));
        }

        private ResourceUsageHistory.GrantUsage toGrantUsage() {
            List<ResourceUsageHistory.UsageRecord> ordered = records.stream()
                    .sorted(Comparator.comparing(TimedRecord::time).thenComparing(timed -> timed.record().getClass().getName()))
                    .map(TimedRecord::record)
                    .toList();
            return new ResourceUsageHistory.GrantUsage(grantId, grantTarget, grantTargetNameAtTime, ordered);
        }
    }

    private record TimedRecord(Instant time, ResourceUsageHistory.UsageRecord record) {}
}
