package com.example.entitlements.persistence;

import com.example.entitlements.domain.BooleanValue;
import com.example.entitlements.domain.EntitlementChangeType;
import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.domain.GrantLookupKey;
import com.example.entitlements.domain.QuantityValue;
import com.example.entitlements.domain.QuotaPeriod;
import com.example.entitlements.domain.QuotaValue;
import com.example.entitlements.domain.RangeValue;
import com.example.entitlements.domain.RateLimitValue;
import com.example.entitlements.domain.SetValue;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.domain.TextValue;
import com.example.entitlements.domain.TimeRangeValue;
import com.example.entitlements.domain.Usage;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.ConsumptionResult;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.EvaluationResult;
import com.example.entitlements.request.RateLimitRequest;
import com.example.entitlements.request.RateLimitResult;
import com.example.entitlements.service.ResourceUsageHistory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresPersistenceIT extends PostgresIntegrationTest {
    @Autowired ObjectMapper mapper;

    @Test
    void registerPersistsAndReloadsHierarchy() {
        registerAcme();
        Tenant reloaded = reload("acme");

        assertEquals("Acme Corp", reloaded.getName());
        assertEquals("root", reloaded.getRootScopeId());
        assertEquals("engineering", reloaded.getScopes().get("backend").getParentScopeId());
        assertTrue(reloaded.getScopes().get("engineering").getChildScopeIds().contains("backend"));
        assertTrue(reloaded.getScopes().get("backend").getSubjectIds().contains("alice"));
        assertEquals("backend", reloaded.getSubjects().get("alice").getScopeId());
        assertEquals("AI API", reloaded.getResources().get("api").name());
        assertEquals("g-eng-quota", reloaded.findGrant(
                new Target(TargetType.SCOPE, "engineering"), "api", "api.requests").orElseThrow().id());
        assertTrue(reloaded.isGrantIndexed(GrantLookupKey.of(
                new Target(TargetType.SCOPE, "engineering"), "api", "api.requests")));
        assertEquals(reloaded.getGrants().size(), reloaded.grantIndexSize());
        assertEquals("example", reloaded.getResources().get("api").metadata().get("provider"));
    }

    @Test
    void allEightEntitlementValuesRoundTripJsonb() {
        registerAcme();
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant until = Instant.parse("2026-09-01T00:00:00Z");
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-bool","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.enabled","value":{"type":"BOOLEAN","value":false}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-qty","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.maxBatch","value":{"type":"QUANTITY","value":12,"unit":"request"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-quota","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":42,"unit":"request","period":"WEEKLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-rate","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.rateLimit","value":{"type":"RATE_LIMIT","capacity":9,"refillTokens":3,"refillPeriod":"PT30S"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-range","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.temperature","value":{"type":"RANGE","min":0.1,"max":1.5,"unit":"value"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-set","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.models","value":{"type":"SET","values":["alpha","beta"]}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-text","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.tier","value":{"type":"TEXT","value":"gold"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-be-window","target":{"type":"SCOPE","id":"backend"},"resourceId":"api","entitlementKey":"api.accessWindow","value":{"type":"TIME_RANGE","from":"2026-08-01T00:00:00Z","until":"2026-09-01T00:00:00Z"}}
                """);

        Tenant reloaded = reload("acme");
        assertEquals(new BooleanValue(false), grant(reloaded, "g-be-bool").value());
        assertEquals(new QuantityValue(new BigDecimal("12"), "request"), grant(reloaded, "g-be-qty").value());
        assertEquals(new QuotaValue(new BigDecimal("42"), "request", QuotaPeriod.WEEKLY), grant(reloaded, "g-be-quota").value());
        assertEquals(new RateLimitValue(new BigDecimal("9"), new BigDecimal("3"), Duration.ofSeconds(30)), grant(reloaded, "g-be-rate").value());
        assertEquals(new RangeValue(new BigDecimal("0.1"), new BigDecimal("1.5"), "value"), grant(reloaded, "g-be-range").value());
        assertEquals(new SetValue(Set.of("alpha", "beta")), grant(reloaded, "g-be-set").value());
        assertEquals(new TextValue("gold"), grant(reloaded, "g-be-text").value());
        assertEquals(new TimeRangeValue(from, until), grant(reloaded, "g-be-window").value());
    }

    @Test
    void evaluateWorksAfterCacheEvictReload() {
        registerAcme();
        reload("acme");
        EvaluationResult result = entitlementService.evaluate(
                new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(500)));
        assertTrue(result.allowed());
        assertEquals("g-eng-quota", result.grantId());
    }

    @Test
    void setAndRemoveEntitlementPersist() {
        registerAcme();
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-alice-q","target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":10,"unit":"request","period":"DAILY"}}
                """);
        assertEquals("g-alice-q", reload("acme").findGrant(
                new Target(TargetType.SUBJECT, "alice"), "api", "api.requests").orElseThrow().id());

        execute("REMOVE_ENTITLEMENT", """
                {"target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.requests"}
                """);
        assertTrue(reload("acme").findGrant(
                new Target(TargetType.SUBJECT, "alice"), "api", "api.requests").isEmpty());
    }

    @Test
    void moveSubjectPersistsAndChangesInheritance() {
        registerAcme();
        execute("MOVE_SUBJECT", "{\"subjectId\":\"alice\",\"newScopeId\":\"marketing\"}");
        Tenant reloaded = reload("acme");
        assertEquals("marketing", reloaded.getSubjects().get("alice").getScopeId());
        EvaluationResult result = entitlementService.evaluate(
                new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(1)));
        assertEquals("g-root-quota", result.grantId());
    }

    @Test
    void moveScopePersists() {
        registerAcme();
        execute("MOVE_SCOPE", "{\"scopeId\":\"backend\",\"newParentScopeId\":\"marketing\"}");
        Tenant reloaded = reload("acme");
        assertEquals("marketing", reloaded.getScopes().get("backend").getParentScopeId());
        assertTrue(reloaded.getScopes().get("marketing").getChildScopeIds().contains("backend"));
        assertFalse(reloaded.getScopes().get("engineering").getChildScopeIds().contains("backend"));
    }

    @Test
    void removeScopeCascadePersistsAndKeepsHistory() {
        registerAcme();
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", BigDecimal.TEN));
        execute("REMOVE_SCOPE", "{\"scopeId\":\"engineering\"}");
        Tenant reloaded = reload("acme");
        assertFalse(reloaded.getScopes().containsKey("backend"));
        assertFalse(reloaded.getSubjects().containsKey("alice"));
        assertTrue(usageHistoryRepository.hasHistory("acme", "api"));
        assertFalse(entitlementHistoryRepository.findByResource("acme", "api").isEmpty());
    }

    @Test
    void removeResourceCleansCurrentStateKeepsHistory() {
        registerAcme();
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.enabled", null));
        execute("REMOVE_RESOURCE", "{\"resourceId\":\"api\"}");
        Tenant reloaded = reload("acme");
        assertFalse(reloaded.getResources().containsKey("api"));
        assertTrue(reloaded.getGrants().values().stream().noneMatch(grant -> grant.resourceId().equals("api")));
        assertTrue(usageHistoryRepository.hasHistory("acme", "api"));
        assertFalse(entitlementHistoryRepository.findByResource("acme", "api").isEmpty());
    }

    @Test
    void logicalGrantUniquenessIsEnforced() {
        registerAcme();
        EntitlementGrant duplicate = new EntitlementGrant(
                "other-id",
                new Target(TargetType.SCOPE, "engineering"),
                "api",
                "api.requests",
                new QuotaValue(new BigDecimal("1"), "request", QuotaPeriod.DAILY));
        assertThrows(RuntimeException.class, () -> tenantRepository.upsertGrant("acme", duplicate));
    }

    @Test
    void quotaConsumePersistsSharedPoolAndSurvivesReload() {
        registerAcme();
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("100")));
        usageService.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("250")));
        reload("acme");
        Usage usage = usageRepository.get("acme", "g-eng-quota");
        assertEquals(0, new BigDecimal("350").compareTo(usage.getConsumed()));
        ConsumptionResult denied = usageService.consume(
                new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("1000001")));
        assertFalse(denied.allowed());
        assertEquals(0, new BigDecimal("350").compareTo(usageRepository.get("acme", "g-eng-quota").getConsumed()));
    }

    @Test
    void personalOverrideUsesSeparatePersistedPool() {
        registerAcme();
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-alice-q","target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":500,"unit":"request","period":"MONTHLY"}}
                """);
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("100")));
        usageService.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("100")));
        reload("acme");
        assertEquals(0, new BigDecimal("100").compareTo(usageRepository.get("acme", "g-alice-q").getConsumed()));
        assertEquals(0, new BigDecimal("100").compareTo(usageRepository.get("acme", "g-eng-quota").getConsumed()));
    }

    @Test
    void quotaLazyPeriodResetStillWorks() {
        registerAcme();
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("50")));
        jdbc.update("""
                UPDATE usage_current
                SET consumed = 50, period_start = '2020-01-01T00:00:00Z', period_end = '2020-02-01T00:00:00Z'
                WHERE tenant_id = 'acme' AND grant_id = 'g-eng-quota'
                """);
        ConsumptionResult result = usageService.consume(
                new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("7")));
        assertTrue(result.allowed());
        assertEquals(0, new BigDecimal("7").compareTo(result.consumed()));
    }

    @Test
    void quotaConsumeAndBucketAreAtomic() {
        registerAcme();
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("5")));
        assertEquals(1, usageHistoryRepository.findBucketsByResource("acme", "api").size());
        assertEquals(0, new BigDecimal("5").compareTo(usageRepository.get("acme", "g-eng-quota").getConsumed()));
    }

    @Test
    void concurrentQuotaConsumeCannotExceedLimit() throws Exception {
        registerAcme();
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-quota","target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":10,"unit":"request","period":"MONTHLY"}}
                """);
        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ConsumptionResult> first = pool.submit(() -> {
                start.await();
                return usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("8")));
            });
            Future<ConsumptionResult> second = pool.submit(() -> {
                start.await();
                return usageService.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("8")));
            });
            start.countDown();
            if (first.get().allowed()) successes.incrementAndGet();
            if (second.get().allowed()) successes.incrementAndGet();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, successes.get());
        assertEquals(0, new BigDecimal("8").compareTo(usageRepository.get("acme", "g-eng-quota").getConsumed()));
    }

    @Test
    void rateLimitStatePersistsAndDeniedWritesNoHistory() {
        registerAcme();
        RateLimitResult ok = rateLimitService.tryConsume(
                new RateLimitRequest("acme", "alice", "api", "api.rateLimit", new BigDecimal("30")));
        assertTrue(ok.allowed());
        reload("acme");
        assertEquals(0, new BigDecimal("70").compareTo(
                rateLimitStateRepository.get("acme", "g-eng-rate").getAvailableTokens()));
        assertEquals(1, usageHistoryRepository.findBucketsByResource("acme", "api").size());

        RateLimitResult denied = rateLimitService.tryConsume(
                new RateLimitRequest("acme", "bob", "api", "api.rateLimit", new BigDecimal("1000")));
        assertFalse(denied.allowed());
        assertEquals(1, usageHistoryRepository.findBucketsByResource("acme", "api").size());
        rateLimitService.peekAvailableTokens("acme", "g-eng-rate",
                (RateLimitValue) grant(reload("acme"), "g-eng-rate").value());
        assertEquals(1, usageHistoryRepository.findBucketsByResource("acme", "api").size());
    }

    @Test
    void concurrentRateLimitDoesNotOverspend() throws Exception {
        registerAcme();
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-rate","target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.rateLimit","value":{"type":"RATE_LIMIT","capacity":10,"refillTokens":10,"refillPeriod":"PT1H"}}
                """);
        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RateLimitResult> first = pool.submit(() -> {
                start.await();
                return rateLimitService.tryConsume(new RateLimitRequest("acme", "alice", "api", "api.rateLimit", new BigDecimal("8")));
            });
            Future<RateLimitResult> second = pool.submit(() -> {
                start.await();
                return rateLimitService.tryConsume(new RateLimitRequest("acme", "bob", "api", "api.rateLimit", new BigDecimal("8")));
            });
            start.countDown();
            if (first.get().allowed()) successes.incrementAndGet();
            if (second.get().allowed()) successes.incrementAndGet();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, successes.get());
        assertEquals(0, new BigDecimal("2").compareTo(
                rateLimitStateRepository.get("acme", "g-eng-rate").getAvailableTokens()));
    }

    @Test
    void usageEventsAndBucketsRoundTripAndFilter() {
        registerAcme();
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.enabled", mapper.valueToTree(true)));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.maxBatch", mapper.valueToTree(5)));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.temperature", mapper.valueToTree(1)));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.tier", mapper.valueToTree("premium")));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("small")));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.accessWindow", null));
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("3")));
        usageService.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("2")));

        reload("acme");
        assertEquals(6, usageHistoryRepository.findEventsByResource("acme", "api").size());
        var history = usageHistoryService.getHistory("acme", "api", null, null);
        assertFalse(history.entitlements().isEmpty());
        var buckets = usageHistoryRepository.findBucketsByResource("acme", "api");
        assertEquals(2, buckets.size());
        assertEquals(1, buckets.stream().filter(bucket -> bucket.subjectId().equals("alice")).count());
        assertEquals(1, buckets.stream().filter(bucket -> bucket.subjectId().equals("bob")).count());
        assertEquals(1, buckets.stream().filter(bucket -> "alice".equals(bucket.subjectId())).findFirst().orElseThrow().operationCount());
        assertEquals(0, new BigDecimal("3").compareTo(
                buckets.stream().filter(bucket -> "alice".equals(bucket.subjectId())).findFirst().orElseThrow().totalConsumed()));

        Instant until = Instant.parse("2000-01-01T00:00:00Z");
        assertTrue(usageHistoryService.getHistory("acme", "api", Instant.parse("1999-01-01T00:00:00Z"), until)
                .entitlements().isEmpty());
    }

    @Test
    void concurrentBucketIncrementsDoNotLoseUpdates() throws Exception {
        registerAcme();
        Target target = new Target(TargetType.SCOPE, "engineering");
        Instant now = Instant.parse("2026-08-14T14:31:00Z");
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    usageHistoryRepository.addToBucket(
                            "acme", "alice", "Alice", "api", "AI API", "api", "api.requests",
                            "g-eng-quota", target, "Engineering", BigDecimal.ONE, now);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }
        var bucket = usageHistoryRepository.findBucketsByResource("acme", "api").getFirst();
        assertEquals(threads, bucket.operationCount());
        assertEquals(0, new BigDecimal(threads).compareTo(bucket.totalConsumed()));
    }

    @Test
    void deletedCurrentObjectsKeepQueryableHistory() {
        registerAcme();
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", BigDecimal.TEN));
        execute("REMOVE_SUBJECT", "{\"subjectId\":\"alice\"}");
        execute("REMOVE_RESOURCE", "{\"resourceId\":\"api\"}");
        var history = usageHistoryService.getHistory("acme", "api", null, null);
        assertEquals("AI API", history.resourceName());
        ResourceUsageHistory.UsageRecord record = history.entitlements().getFirst().grants().getFirst().usage().getFirst();
        String subjectName = switch (record) {
            case ResourceUsageHistory.BucketUsage bucket -> bucket.subjectNameAtTime();
            case ResourceUsageHistory.EventUsage event -> event.subjectNameAtTime();
        };
        assertEquals("Alice", subjectName);
        assertFalse(entitlementHistoryRepository.findByResource("acme", "api").isEmpty());
    }

    @Test
    void entitlementHistorySemanticsPersistAndSurviveReload() {
        registerAcme();
        List<EntitlementHistoryEvent> created = entitlementHistoryRepository.findByResource("acme", "api");
        assertTrue(created.stream().allMatch(event -> event.changeType() == EntitlementChangeType.CREATED));
        int initial = created.size();

        execute("SET_ENTITLEMENT", """
                {"grantId":"g-alice-enabled","target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.enabled","value":{"type":"BOOLEAN","value":true}}
                """);
        assertEquals(initial + 1, entitlementHistoryRepository.findByResource("acme", "api").size());

        execute("SET_ENTITLEMENT", """
                {"grantId":"g-alice-enabled","target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.enabled","value":{"type":"BOOLEAN","value":false}}
                """);
        List<EntitlementHistoryEvent> afterUpdate = entitlementHistoryRepository.findByResource("acme", "api");
        assertEquals(1, afterUpdate.stream().filter(event -> event.changeType() == EntitlementChangeType.UPDATED).count());

        execute("SET_ENTITLEMENT", """
                {"grantId":"g-alice-enabled","target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.enabled","value":{"type":"BOOLEAN","value":false}}
                """);
        assertEquals(afterUpdate.size(), entitlementHistoryRepository.findByResource("acme", "api").size());

        execute("REMOVE_ENTITLEMENT", """
                {"target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.enabled"}
                """);
        assertEquals(1, entitlementHistoryRepository.findByResource("acme", "api").stream()
                .filter(event -> event.changeType() == EntitlementChangeType.REMOVED).count());

        reload("acme");
        assertFalse(entitlementHistoryRepository.findByResource("acme", "api").isEmpty());
    }

    @Test
    void quotaAndHistoryRollBackTogetherWhenBucketWriteFails() {
        registerAcme();
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_buckets() RETURNS trigger AS $$
                BEGIN
                  RAISE EXCEPTION 'forced bucket failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER trg_fail_buckets BEFORE INSERT OR UPDATE ON usage_buckets FOR EACH ROW EXECUTE FUNCTION fail_buckets()");
        try {
            assertThrows(RuntimeException.class, () -> usageService.consume(
                    new ConsumptionRequest("acme", "alice", "api", "api.requests", BigDecimal.TEN)));
            assertTrue(usageRepository.get("acme", "g-eng-quota") == null
                    || usageRepository.get("acme", "g-eng-quota").getConsumed().signum() == 0);
            assertTrue(usageHistoryRepository.findBucketsByResource("acme", "api").isEmpty());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_buckets ON usage_buckets");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_buckets()");
        }
    }

    @Test
    void failedRegistrationLeavesNoPartialTenant() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_grants() RETURNS trigger AS $$
                BEGIN
                  RAISE EXCEPTION 'forced grant failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER trg_fail_grants BEFORE INSERT ON entitlement_grants FOR EACH ROW EXECUTE FUNCTION fail_grants()");
        try {
            assertThrows(RuntimeException.class, this::registerAcme);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM scopes", Integer.class));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_grants ON entitlement_grants");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_grants()");
        }
    }

    @Test
    void failedGrantMutationWritesNoHistory() {
        registerAcme();
        int before = entitlementHistoryRepository.findByResource("acme", "api").size();
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_history() RETURNS trigger AS $$
                BEGIN
                  RAISE EXCEPTION 'forced history failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER trg_fail_history BEFORE INSERT ON entitlement_history FOR EACH ROW EXECUTE FUNCTION fail_history()");
        try {
            assertThrows(RuntimeException.class, () -> execute("SET_ENTITLEMENT", """
                    {"grantId":"g-alice-enabled","target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.enabled","value":{"type":"BOOLEAN","value":false}}
                    """));
            reload("acme");
            assertTrue(registry.getRequired("acme").findGrant(
                    new Target(TargetType.SUBJECT, "alice"), "api", "api.enabled").isEmpty());
            assertEquals(before, entitlementHistoryRepository.findByResource("acme", "api").size());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_history ON entitlement_history");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_history()");
        }
    }

    private void execute(String type, String payload) {
        try {
            JsonNode node = mapper.readTree(payload);
            commandService.execute(new CommandRequest(CommandType.valueOf(type), "acme", node));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static EntitlementGrant grant(Tenant tenant, String grantId) {
        EntitlementGrant grant = tenant.getGrants().get(grantId);
        assertNotNull(grant);
        return grant;
    }
}
