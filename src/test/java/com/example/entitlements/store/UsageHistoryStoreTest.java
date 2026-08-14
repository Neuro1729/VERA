package com.example.entitlements.store;

import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.UsageBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageHistoryStoreTest {
    private UsageHistoryStore store;

    @BeforeEach
    void setUp() {
        store = new UsageHistoryStore();
    }

    @Test
    void floorsOccurredAtToUtcFiveMinuteWindow() {
        Instant occurredAt = Instant.parse("2026-08-14T14:37:42Z");
        assertEquals(Instant.parse("2026-08-14T14:35:00Z"), UsageHistoryStore.bucketStart(occurredAt));
        assertEquals(Instant.parse("2026-08-14T14:40:00Z"), UsageHistoryStore.bucketEnd(UsageHistoryStore.bucketStart(occurredAt)));
    }

    @Test
    void isolatesTenantsAndResources() {
        add("acme", "alice", "api", "g1", "1");
        add("acme", "alice", "gpu", "g2", "2");
        add("globex", "alice", "api", "g1", "3");

        assertEquals(1, store.findBucketsByResource("acme", "api").size());
        assertEquals(1, store.findBucketsByResource("acme", "gpu").size());
        assertEquals(1, store.findBucketsByResource("globex", "api").size());
        assertEquals(0, new BigDecimal("1").compareTo(store.findBucketsByResource("acme", "api").getFirst().totalConsumed()));
    }

    @Test
    void concurrentUpdatesDoNotLoseIncrements() throws Exception {
        Instant now = Instant.parse("2026-08-14T14:31:00Z");
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                add("acme", "alice", "api", "g-eng-quota", "1", now);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) future.get();
        pool.shutdownNow();

        List<UsageBucket> buckets = store.findBucketsByResource("acme", "api");
        assertEquals(1, buckets.size());
        assertEquals(0, new BigDecimal("50").compareTo(buckets.getFirst().totalConsumed()));
        assertEquals(50, buckets.getFirst().operationCount());
    }

    private void add(String tenantId, String subjectId, String resourceId, String grantId, String amount) {
        add(tenantId, subjectId, resourceId, grantId, amount, Instant.parse("2026-08-14T14:31:00Z"));
    }

    private void add(
            String tenantId,
            String subjectId,
            String resourceId,
            String grantId,
            String amount,
            Instant occurredAt
    ) {
        store.addToBucket(
                tenantId,
                subjectId,
                "Alice",
                resourceId,
                "name",
                "kind",
                "api.requests",
                grantId,
                new Target(TargetType.SCOPE, "engineering"),
                "Engineering",
                new BigDecimal(amount),
                occurredAt);
    }
}
