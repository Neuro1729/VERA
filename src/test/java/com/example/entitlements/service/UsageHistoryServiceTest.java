package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.domain.*;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.RateLimitRequest;
import com.example.entitlements.request.RegistrationRequest;
import com.example.entitlements.request.TenantInput;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageHistoryStore;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.MutableClock;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class UsageHistoryServiceTest {
    private TenantRegistry registry;
    private Tenant tenant;
    private MutableClock clock;
    private UsageHistoryStore historyStore;
    private UsageService usageService;
    private RateLimitService rateLimitService;
    private EntitlementService entitlementService;
    private ResourceUseService resourceUseService;
    private UsageHistoryService historyService;
    private CommandService commandService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
        historyStore = new UsageHistoryStore();
        clock = new MutableClock(Instant.parse("2026-08-14T14:31:00Z"));
        mapper = new ObjectMapper().findAndRegisterModules();
        GrantResolutionCache cache = new GrantResolutionCache();
        EntitlementResolver resolver = new EntitlementResolver(cache);
        UsageStore usageStore = new UsageStore();
        usageService = new UsageService(registry, usageStore, resolver, historyStore, clock);
        rateLimitService = new RateLimitService(registry, resolver, historyStore, clock);
        entitlementService = new EntitlementService(registry, resolver, usageService, rateLimitService, clock);
        resourceUseService = new ResourceUseService(registry, entitlementService, historyStore, mapper, clock);
        historyService = new UsageHistoryService(registry, historyStore);
        commandService = new CommandService(
                registry,
                usageStore,
                mapper,
                cache,
                new ResolutionCacheInvalidator(cache),
                rateLimitService,
                new EntitlementHistoryService(registry, new EntitlementHistoryStore(), clock));
        tenant = new RegistrationService(registry, new EntitlementHistoryService(registry, new EntitlementHistoryStore(), clock))
                .register(TestFixtures.registration());
    }

    @Test
    void successfulQuotaConsumeCreatesFiveMinuteBucket() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("40")));

        UsageBucket bucket = onlyBucket("api");
        assertEquals("alice", bucket.subjectId());
        assertEquals("Alice", bucket.subjectNameAtTime());
        assertEquals("g-eng-quota", bucket.grantId());
        assertEquals(new Target(TargetType.SCOPE, "engineering"), bucket.grantTarget());
        assertEquals("Engineering", bucket.grantTargetNameAtTime());
        assertEquals(Instant.parse("2026-08-14T14:30:00Z"), bucket.bucketStart());
        assertEquals(Instant.parse("2026-08-14T14:35:00Z"), bucket.bucketEnd());
        assertEquals(0, new BigDecimal("40").compareTo(bucket.totalConsumed()));
        assertEquals(1, bucket.operationCount());
        assertEquals(Instant.parse("2026-08-14T14:31:00Z"), bucket.firstOccurredAt());
        assertEquals(Instant.parse("2026-08-14T14:31:00Z"), bucket.lastOccurredAt());
        assertEquals("AI API", bucket.resourceNameAtTime());
        assertEquals("api", bucket.resourceKindAtTime());
    }

    @Test
    void matchingQuotaConsumesMergeIntoOneBucket() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("40")));
        clock.setInstant(Instant.parse("2026-08-14T14:33:00Z"));
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("20")));

        UsageBucket bucket = onlyBucket("api");
        assertEquals(0, new BigDecimal("60").compareTo(bucket.totalConsumed()));
        assertEquals(2, bucket.operationCount());
        assertEquals(Instant.parse("2026-08-14T14:31:00Z"), bucket.firstOccurredAt());
        assertEquals(Instant.parse("2026-08-14T14:33:00Z"), bucket.lastOccurredAt());
    }

    @Test
    void nextFiveMinuteWindowCreatesADifferentBucket() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("10")));
        clock.setInstant(Instant.parse("2026-08-14T14:36:00Z"));
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("5")));

        List<UsageBucket> buckets = historyStore.findBucketsByResource("acme", "api");
        assertEquals(2, buckets.size());
        assertTrue(buckets.stream().anyMatch(bucket -> bucket.bucketStart().equals(Instant.parse("2026-08-14T14:30:00Z"))));
        assertTrue(buckets.stream().anyMatch(bucket -> bucket.bucketStart().equals(Instant.parse("2026-08-14T14:35:00Z"))));
    }

    @Test
    void differentGrantIdsInSameWindowStaySeparate() {
        tenant.putGrant(new EntitlementGrant(
                "g-alice-quota",
                new Target(TargetType.SUBJECT, "alice"),
                "api",
                "api.requests",
                new QuotaValue(new BigDecimal("500"), "request", QuotaPeriod.MONTHLY)));

        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("100")));
        usageService.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("50")));

        List<UsageBucket> buckets = historyStore.findBucketsByResource("acme", "api");
        assertEquals(2, buckets.size());
        assertTrue(buckets.stream().anyMatch(bucket -> "g-alice-quota".equals(bucket.grantId())
                && bucket.totalConsumed().compareTo(new BigDecimal("100")) == 0
                && "Alice".equals(bucket.grantTargetNameAtTime())
                && "Alice".equals(bucket.subjectNameAtTime())));
        assertTrue(buckets.stream().anyMatch(bucket -> "g-eng-quota".equals(bucket.grantId())
                && bucket.totalConsumed().compareTo(new BigDecimal("50")) == 0));
    }

    @Test
    void differentSubjectsCreateSeparateBuckets() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("10")));
        usageService.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("7")));

        List<UsageBucket> buckets = historyStore.findBucketsByResource("acme", "api");
        assertEquals(2, buckets.size());
        assertTrue(buckets.stream().anyMatch(bucket -> "alice".equals(bucket.subjectId())));
        assertTrue(buckets.stream().anyMatch(bucket -> "bob".equals(bucket.subjectId())));
    }

    @Test
    void differentResourcesAndTenantsAreIsolated() {
        tenant.putGrant(new EntitlementGrant(
                "g-eng-hours",
                new Target(TargetType.SCOPE, "engineering"),
                "gpu",
                "gpu.hours",
                new QuotaValue(new BigDecimal("5000"), "hour", QuotaPeriod.MONTHLY)));
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", BigDecimal.ONE));
        usageService.consume(new ConsumptionRequest("acme", "alice", "gpu", "gpu.hours", BigDecimal.TEN));

        RegistrationRequest base = TestFixtures.registration();
        new RegistrationService(registry, new EntitlementHistoryService(registry, new EntitlementHistoryStore(), clock))
                .register(new RegistrationRequest(new TenantInput("globex", "Globex"), base.structure(), base.resources(), base.grants()));
        usageService.consume(new ConsumptionRequest("globex", "alice", "api", "api.requests", new BigDecimal("3")));

        assertEquals(1, historyStore.findBucketsByResource("acme", "api").size());
        assertEquals(1, historyStore.findBucketsByResource("acme", "gpu").size());
        assertEquals(1, historyStore.findBucketsByResource("globex", "api").size());
        assertEquals(0, new BigDecimal("1").compareTo(historyStore.findBucketsByResource("acme", "api").getFirst().totalConsumed()));
        assertEquals(0, new BigDecimal("3").compareTo(historyStore.findBucketsByResource("globex", "api").getFirst().totalConsumed()));
    }

    @Test
    void deniedQuotaConsumptionCreatesNoHistory() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("1000001")));
        assertTrue(historyStore.findBucketsByResource("acme", "api").isEmpty());
    }

    @Test
    void successfulRateLimitConsumeUpdatesBucketAndDeniedOrPeekDoNot() {
        assertTrue(rateLimitService.tryConsume(new RateLimitRequest("acme", "alice", "api", "api.rateLimit", new BigDecimal("30"))).allowed());
        UsageBucket bucket = onlyBucket("api");
        assertEquals("g-eng-rate", bucket.grantId());
        assertEquals("Alice", bucket.subjectNameAtTime());
        assertEquals("Engineering", bucket.grantTargetNameAtTime());
        assertEquals(0, new BigDecimal("30").compareTo(bucket.totalConsumed()));
        assertEquals(1, bucket.operationCount());

        assertFalse(rateLimitService.tryConsume(new RateLimitRequest("acme", "alice", "api", "api.rateLimit", new BigDecimal("100"))).allowed());
        rateLimitService.peekAvailableTokens("acme", "g-eng-rate",
                (RateLimitValue) tenant.getGrants().get("g-eng-rate").value());
        rateLimitService.availableTokens("acme", "alice", "api", "api.rateLimit");

        assertEquals(1, historyStore.findBucketsByResource("acme", "api").size());
        assertEquals(1, onlyBucket("api").operationCount());
    }

    @Test
    void descriptiveCommittedUseWritesExactEventsAndEvaluateDoesNot() {
        assertTrue(entitlementService.evaluate(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("large"))).allowed());
        assertTrue(historyStore.findEventsByResource("acme", "api").isEmpty());

        clock.setInstant(Instant.parse("2026-08-14T10:32:14Z"));
        assertTrue(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("large"))).allowed());
        clock.setInstant(Instant.parse("2026-08-14T11:15:21Z"));
        assertTrue(resourceUseService.commitUse(new EvaluationRequest(
                "acme", "alice", "api", "api.models", mapper.createArrayNode().add("small").add("large"))).allowed());

        assertTrue(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.temperature", mapper.valueToTree(1.5))).allowed());
        assertTrue(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.enabled", null)).allowed());
        assertTrue(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.accessWindow", null)).allowed());
        assertTrue(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.tier", mapper.valueToTree("premium"))).allowed());
        assertTrue(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.maxBatch", mapper.valueToTree(25))).allowed());

        List<UsageEvent> events = historyStore.findEventsByResource("acme", "api");
        assertEquals(7, events.size());
        List<UsageEvent> setEvents = events.stream().filter(event -> "api.models".equals(event.entitlementKey())).toList();
        assertEquals(2, setEvents.size());
        assertEquals(Instant.parse("2026-08-14T10:32:14Z"), setEvents.getFirst().occurredAt());
        assertEquals(Instant.parse("2026-08-14T11:15:21Z"), setEvents.get(1).occurredAt());
        assertTrue(setEvents.getFirst().usedValue().isArray());
        assertEquals("large", setEvents.getFirst().usedValue().get(0).asText());
        assertEquals("Alice", setEvents.getFirst().subjectNameAtTime());
        assertEquals("Engineering", setEvents.getFirst().grantTargetNameAtTime());

        UsageEvent range = events.stream().filter(event -> "api.temperature".equals(event.entitlementKey())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1.5").compareTo(range.usedValue().get("value").decimalValue()));
        assertEquals("value", range.usedValue().get("unit").asText());

        assertTrue(events.stream().anyMatch(event -> "api.enabled".equals(event.entitlementKey()) && event.usedValue().asBoolean()));
        assertTrue(events.stream().anyMatch(event -> "api.tier".equals(event.entitlementKey()) && "premium".equals(event.usedValue().asText())));
        assertTrue(events.stream().anyMatch(event -> "api.maxBatch".equals(event.entitlementKey())
                && event.usedValue().get("value").intValue() == 25));
        assertTrue(events.stream().anyMatch(event -> "api.accessWindow".equals(event.entitlementKey())));
    }

    @Test
    void deniedCommittedUseAndQuotaViaUseCreateNoEvents() {
        assertFalse(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("unknown"))).allowed());
        clock.setInstant(Instant.parse("2026-10-01T00:00:00Z"));
        assertFalse(resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.accessWindow", null)).allowed());
        assertThrows(IllegalArgumentException.class, () -> resourceUseService.commitUse(
                new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(1))));
        assertTrue(historyStore.findEventsByResource("acme", "api").isEmpty());
    }

    @Test
    void resourceHistoryGroupsByEntitlementThenGrantChronologically() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("40")));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("large")));

        ResourceUsageHistory history = historyService.getHistory("acme", "api", null, null);
        assertEquals("api", history.resourceId());
        assertEquals("AI API", history.resourceName());
        assertEquals("api", history.resourceKind());
        assertEquals(List.of("api.requests", "api.models"), history.entitlements().stream()
                .map(ResourceUsageHistory.EntitlementUsage::entitlementKey)
                .toList());

        ResourceUsageHistory.GrantUsage quotaGrant = history.entitlements().getFirst().grants().getFirst();
        assertEquals("g-eng-quota", quotaGrant.grantId());
        assertEquals(new Target(TargetType.SCOPE, "engineering"), quotaGrant.grantTarget());
        assertEquals("Engineering", quotaGrant.grantTargetNameAtTime());
        ResourceUsageHistory.BucketUsage quotaUsage = (ResourceUsageHistory.BucketUsage) quotaGrant.usage().getFirst();
        assertEquals("Alice", quotaUsage.subjectNameAtTime());

        ResourceUsageHistory.GrantUsage setGrant = history.entitlements().get(1).grants().getFirst();
        assertEquals("g-models", setGrant.grantId());
        assertEquals("Engineering", setGrant.grantTargetNameAtTime());
        ResourceUsageHistory.EventUsage setUsage = (ResourceUsageHistory.EventUsage) setGrant.usage().getFirst();
        assertEquals("Alice", setUsage.subjectNameAtTime());
    }

    @Test
    void fromInclusiveUntilExclusiveFiltersBuckets() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("10")));
        clock.setInstant(Instant.parse("2026-08-14T14:36:00Z"));
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("5")));
        clock.setInstant(Instant.parse("2026-08-14T14:41:00Z"));
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("2")));

        ResourceUsageHistory filtered = historyService.getHistory(
                "acme",
                "api",
                Instant.parse("2026-08-14T14:35:00Z"),
                Instant.parse("2026-08-14T14:40:00Z"));
        List<ResourceUsageHistory.UsageRecord> usage = filtered.entitlements().getFirst().grants().getFirst().usage();
        assertEquals(1, usage.size());
        ResourceUsageHistory.BucketUsage bucket = (ResourceUsageHistory.BucketUsage) usage.getFirst();
        assertEquals(Instant.parse("2026-08-14T14:35:00Z"), bucket.bucketStart());
        assertEquals(0, new BigDecimal("5").compareTo(bucket.totalConsumed()));
    }

    @Test
    void overlappingQueryIncludesWholeBucketWithoutProrating() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("40")));

        Instant start = Instant.parse("2026-08-14T14:30:00Z");
        assertEquals(List.of(start), bucketStarts("acme", "api", Instant.parse("2026-08-14T14:32:00Z"), Instant.parse("2026-08-14T14:40:00Z")));
        assertTrue(bucketStarts("acme", "api", Instant.parse("2026-08-14T14:35:00Z"), Instant.parse("2026-08-14T14:40:00Z")).isEmpty());
        assertTrue(bucketStarts("acme", "api", Instant.parse("2026-08-14T14:20:00Z"), Instant.parse("2026-08-14T14:30:00Z")).isEmpty());
        assertEquals(List.of(start), bucketStarts("acme", "api", Instant.parse("2026-08-14T14:20:00Z"), Instant.parse("2026-08-14T14:31:00Z")));
        assertEquals(List.of(start), bucketStarts("acme", "api", Instant.parse("2026-08-14T14:32:00Z"), null));
        assertTrue(bucketStarts("acme", "api", null, Instant.parse("2026-08-14T14:30:00Z")).isEmpty());

        ResourceUsageHistory overlapping = historyService.getHistory(
                "acme", "api", Instant.parse("2026-08-14T14:32:00Z"), Instant.parse("2026-08-14T14:40:00Z"));
        ResourceUsageHistory.BucketUsage bucket = (ResourceUsageHistory.BucketUsage) overlapping.entitlements().getFirst().grants().getFirst().usage().getFirst();
        assertEquals(0, new BigDecimal("40").compareTo(bucket.totalConsumed()));
        assertEquals(1, bucket.operationCount());
    }

    @Test
    void exactEventsUseFromInclusiveUntilExclusive() {
        clock.setInstant(Instant.parse("2026-08-14T14:32:00Z"));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("large")));

        assertEquals(1, eventCount("acme", "api", Instant.parse("2026-08-14T14:32:00Z"), Instant.parse("2026-08-14T14:33:00Z")));
        assertEquals(0, eventCount("acme", "api", Instant.parse("2026-08-14T14:33:00Z"), Instant.parse("2026-08-14T14:40:00Z")));
        assertEquals(0, eventCount("acme", "api", Instant.parse("2026-08-14T14:30:00Z"), Instant.parse("2026-08-14T14:32:00Z")));
        assertEquals(1, eventCount("acme", "api", Instant.parse("2026-08-14T14:32:00Z"), null));
        assertEquals(0, eventCount("acme", "api", null, Instant.parse("2026-08-14T14:32:00Z")));
    }

    @Test
    void currentResourceWithNoHistoryReturnsEmptyUsage() {
        ResourceUsageHistory history = historyService.getHistory("acme", "gpu", null, null);
        assertEquals("gpu", history.resourceId());
        assertEquals("GPU Cluster", history.resourceName());
        assertEquals("compute", history.resourceKind());
        assertTrue(history.entitlements().isEmpty());
    }

    @Test
    void unknownResourceWithNoHistoryIsNotFound() {
        assertThrows(NoSuchElementException.class, () -> historyService.getHistory("acme", "missing", null, null));
        assertThrows(NoSuchElementException.class, () -> historyService.getHistory("missing", "api", null, null));
    }

    @Test
    void deletedResourceGrantAndSubjectKeepHistoricalUsage() throws Exception {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("20")));
        resourceUseService.commitUse(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("large")));

        commandService.execute(new CommandRequest(CommandType.REMOVE_SUBJECT, "acme", mapper.readTree("{\"subjectId\":\"alice\"}")));
        commandService.execute(new CommandRequest(
                CommandType.REMOVE_ENTITLEMENT,
                "acme",
                mapper.readTree("{\"target\":{\"type\":\"SCOPE\",\"id\":\"engineering\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\"}")));
        commandService.execute(new CommandRequest(CommandType.REMOVE_SCOPE, "acme", mapper.readTree("{\"scopeId\":\"engineering\"}")));
        commandService.execute(new CommandRequest(CommandType.REMOVE_RESOURCE, "acme", mapper.readTree("{\"resourceId\":\"api\"}")));

        assertFalse(tenant.getResources().containsKey("api"));
        assertFalse(tenant.getGrants().containsKey("g-eng-quota"));
        assertFalse(tenant.getSubjects().containsKey("alice"));
        assertFalse(tenant.getScopes().containsKey("engineering"));

        ResourceUsageHistory history = historyService.getHistory("acme", "api", null, null);
        assertEquals("AI API", history.resourceName());
        assertEquals("api", history.resourceKind());
        ResourceUsageHistory.EntitlementUsage quota = history.entitlements().stream()
                .filter(entitlement -> "api.requests".equals(entitlement.entitlementKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("g-eng-quota", quota.grants().getFirst().grantId());
        assertEquals(new Target(TargetType.SCOPE, "engineering"), quota.grants().getFirst().grantTarget());
        assertEquals("Engineering", quota.grants().getFirst().grantTargetNameAtTime());
        ResourceUsageHistory.BucketUsage quotaUsage = (ResourceUsageHistory.BucketUsage) quota.grants().getFirst().usage().getFirst();
        assertEquals("alice", quotaUsage.subjectId());
        assertEquals("Alice", quotaUsage.subjectNameAtTime());

        ResourceUsageHistory.EntitlementUsage models = history.entitlements().stream()
                .filter(entitlement -> "api.models".equals(entitlement.entitlementKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("Engineering", models.grants().getFirst().grantTargetNameAtTime());
        ResourceUsageHistory.EventUsage modelUsage = (ResourceUsageHistory.EventUsage) models.grants().getFirst().usage().getFirst();
        assertEquals("alice", modelUsage.subjectId());
        assertEquals("Alice", modelUsage.subjectNameAtTime());
    }

    @Test
    void quotaMustNotBeDoubleRecordedAsAnEvent() {
        usageService.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("8")));
        assertEquals(1, historyStore.findBucketsByResource("acme", "api").size());
        assertTrue(historyStore.findEventsByResource("acme", "api").isEmpty());
    }

    private UsageBucket onlyBucket(String resourceId) {
        List<UsageBucket> buckets = historyStore.findBucketsByResource("acme", resourceId);
        assertEquals(1, buckets.size());
        return buckets.getFirst();
    }

    private List<Instant> bucketStarts(String tenantId, String resourceId, Instant from, Instant until) {
        return historyService.getHistory(tenantId, resourceId, from, until).entitlements().stream()
                .flatMap(entitlement -> entitlement.grants().stream())
                .flatMap(grant -> grant.usage().stream())
                .filter(ResourceUsageHistory.BucketUsage.class::isInstance)
                .map(ResourceUsageHistory.BucketUsage.class::cast)
                .map(ResourceUsageHistory.BucketUsage::bucketStart)
                .toList();
    }

    private long eventCount(String tenantId, String resourceId, Instant from, Instant until) {
        return historyService.getHistory(tenantId, resourceId, from, until).entitlements().stream()
                .flatMap(entitlement -> entitlement.grants().stream())
                .flatMap(grant -> grant.usage().stream())
                .filter(ResourceUsageHistory.EventUsage.class::isInstance)
                .count();
    }
}
