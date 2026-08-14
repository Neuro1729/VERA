package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.cache.ResolutionKey;
import com.example.entitlements.domain.*;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageHistoryStore;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.MutableClock;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDistributionServiceTest {
    private TenantRegistry registry;
    private UsageStore usageStore;
    private Tenant tenant;
    private MutableClock clock;
    private ResourceDistributionService distributionService;
    private CommandService commandService;
    private UsageService usageService;
    private EntitlementResolver resolver;
    private GrantResolutionCache cache;
    private RateLimitService rateLimitService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        registry = new TenantRegistry();
        usageStore = new UsageStore();
        tenant = TestFixtures.registeredTenant(registry);
        clock = new MutableClock(Instant.parse("2026-08-14T12:00:00Z"));
        cache = new GrantResolutionCache();
        resolver = new EntitlementResolver(cache);
        rateLimitService = new RateLimitService(registry, resolver, new UsageHistoryStore(), clock);
        distributionService = new ResourceDistributionService(registry, usageStore, rateLimitService, clock);
        mapper = new ObjectMapper().findAndRegisterModules();
        commandService = new CommandService(
                registry, usageStore, mapper, cache, new ResolutionCacheInvalidator(cache), rateLimitService,
                new EntitlementHistoryService(registry, new EntitlementHistoryStore(), clock));
        usageService = new UsageService(registry, usageStore, resolver, new UsageHistoryStore(), clock);
        seedGpuDistributionFixture();
    }

    private void seedGpuDistributionFixture() throws Exception {
        execute("""
                {"parentScopeId":"engineering","scope":{"id":"platform","kind":"team","name":"Platform"}}
                """, CommandType.ADD_SCOPE);
        execute("""
                {"scopeId":"engineering","subject":{"id":"david","kind":"employee","name":"David"}}
                """, CommandType.ADD_SUBJECT);

        tenant.putGrant(new EntitlementGrant(
                "g-eng-hours",
                new Target(TargetType.SCOPE, "engineering"),
                "gpu",
                "gpu.hours",
                new QuotaValue(new BigDecimal("5000"), "hour", QuotaPeriod.MONTHLY)));
        tenant.putGrant(new EntitlementGrant(
                "g-backend-hours",
                new Target(TargetType.SCOPE, "backend"),
                "gpu",
                "gpu.hours",
                new QuotaValue(new BigDecimal("8000"), "hour", QuotaPeriod.MONTHLY)));
        tenant.putGrant(new EntitlementGrant(
                "g-david-hours",
                new Target(TargetType.SUBJECT, "david"),
                "gpu",
                "gpu.hours",
                new QuotaValue(new BigDecimal("1000"), "hour", QuotaPeriod.MONTHLY)));
        tenant.putGrant(new EntitlementGrant(
                "g-eng-enabled",
                new Target(TargetType.SCOPE, "engineering"),
                "gpu",
                "gpu.enabled",
                new BooleanValue(true)));
    }

    @Test
    void multipleImmediateChildScopesAreReturned() {
        ResourceDistributionResult result = distributionService.distribute("acme", "gpu", "engineering");
        Set<String> childIds = childIdsFor(result, "gpu.hours");
        assertTrue(childIds.containsAll(Set.of("backend", "ml", "platform")));
    }

    @Test
    void immediateSubjectChildrenAreReturned() {
        ResourceDistributionResult result = distributionService.distribute("acme", "gpu", "engineering");
        Set<String> childIds = childIdsFor(result, "gpu.hours");
        assertTrue(childIds.contains("david"));
    }

    @Test
    void grandchildrenAreNotReturned() {
        ResourceDistributionResult result = distributionService.distribute("acme", "gpu", "engineering");
        Set<String> childIds = childIdsFor(result, "gpu.hours");
        assertFalse(childIds.contains("alice"));
        assertFalse(childIds.contains("bob"));
        assertFalse(childIds.contains("charlie"));
    }

    @Test
    void childInheritsChosenScopeGrant() {
        ResourceDistributionResult.GrantDistribution g1 = grantFor(resultHours(), "g-eng-hours");
        Set<String> ids = g1.children().stream().map(ResourceDistributionResult.Child::id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(Set.of("ml", "platform")));
        assertEquals(new Target(TargetType.SCOPE, "engineering"), g1.source());
    }

    @Test
    void childScopeDirectOverrideWins() {
        ResourceDistributionResult.GrantDistribution g2 = grantFor(resultHours(), "g-backend-hours");
        assertEquals(List.of("backend"), g2.children().stream().map(ResourceDistributionResult.Child::id).toList());
        assertEquals(new Target(TargetType.SCOPE, "backend"), g2.source());
    }

    @Test
    void subjectDirectOverrideWins() {
        ResourceDistributionResult.GrantDistribution g3 = grantFor(resultHours(), "g-david-hours");
        assertEquals(List.of("david"), g3.children().stream().map(ResourceDistributionResult.Child::id).toList());
        assertEquals(new Target(TargetType.SUBJECT, "david"), g3.source());
    }

    @Test
    void entitlementKeysResolveIndependently() {
        ResourceDistributionResult result = distributionService.distribute("acme", "gpu", "engineering");
        assertEquals(1, entitlement(result, "gpu.enabled").grants().size());
        assertEquals(3, entitlement(result, "gpu.hours").grants().size());
    }

    @Test
    void childrenSharingGrantAreGroupedOnce() {
        ResourceDistributionResult.EntitlementDistribution hours = resultHours();
        long engGrantCount = hours.grants().stream().filter(g -> g.grantId().equals("g-eng-hours")).count();
        assertEquals(1, engGrantCount);
        assertEquals(2, grantFor(hours, "g-eng-hours").children().size());
    }

    @Test
    void sharedGrantUsageIsComputedOnceConceptually() {
        usageStore.put("g-eng-hours", new Usage(
                "g-eng-hours",
                new BigDecimal("3200"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")));

        ResourceDistributionResult.GrantDistribution g1 = grantFor(resultHours(), "g-eng-hours");
        ResourceDistributionResult.QuotaRuntime runtime = (ResourceDistributionResult.QuotaRuntime) g1.runtime();
        assertEquals(0, new BigDecimal("3200").compareTo(runtime.consumed()));
        assertEquals(0, new BigDecimal("1800").compareTo(runtime.remaining()));
        assertEquals(2, g1.children().size());
    }

    @Test
    void quotaRuntimeExposesLimitConsumedRemainingAndPeriod() {
        usageStore.put("g-backend-hours", new Usage(
                "g-backend-hours",
                new BigDecimal("2000"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")));

        ResourceDistributionResult.QuotaRuntime runtime =
                (ResourceDistributionResult.QuotaRuntime) grantFor(resultHours(), "g-backend-hours").runtime();
        assertEquals(0, new BigDecimal("8000").compareTo(runtime.limit()));
        assertEquals("hour", runtime.unit());
        assertEquals(QuotaPeriod.MONTHLY, runtime.period());
        assertEquals(0, new BigDecimal("2000").compareTo(runtime.consumed()));
        assertEquals(0, new BigDecimal("6000").compareTo(runtime.remaining()));
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), runtime.periodStart());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), runtime.periodEnd());
    }

    @Test
    void quotaWithNoUsageReportsZeroConsumedAndFullRemainingWithoutCreatingUsage() {
        assertNull(usageStore.get("g-david-hours"));
        ResourceDistributionResult.QuotaRuntime runtime =
                (ResourceDistributionResult.QuotaRuntime) grantFor(resultHours(), "g-david-hours").runtime();
        assertEquals(0, BigDecimal.ZERO.compareTo(runtime.consumed()));
        assertEquals(0, new BigDecimal("1000").compareTo(runtime.remaining()));
        assertNull(usageStore.get("g-david-hours"));
    }

    @Test
    void booleanRuntimeIsReturned() {
        ResourceDistributionResult.BooleanRuntime runtime =
                (ResourceDistributionResult.BooleanRuntime) entitlement(
                        distributionService.distribute("acme", "gpu", "engineering"), "gpu.enabled")
                        .grants().getFirst().runtime();
        assertTrue(runtime.value());
    }

    @Test
    void quantityRangeSetTextAndTimeRangeRuntimesAreReturned() {
        ResourceDistributionResult api = distributionService.distribute("acme", "api", "engineering");

        ResourceDistributionResult.QuantityRuntime quantity =
                (ResourceDistributionResult.QuantityRuntime) entitlement(api, "api.maxBatch").grants().getFirst().runtime();
        assertEquals(0, new BigDecimal("100").compareTo(quantity.value()));

        ResourceDistributionResult.RangeRuntime range =
                (ResourceDistributionResult.RangeRuntime) entitlement(api, "api.temperature").grants().getFirst().runtime();
        assertEquals(0, new BigDecimal("0").compareTo(range.min()));
        assertEquals(0, new BigDecimal("2").compareTo(range.max()));

        ResourceDistributionResult.SetRuntime set =
                (ResourceDistributionResult.SetRuntime) entitlement(api, "api.models").grants().getFirst().runtime();
        assertEquals(Set.of("small", "large"), set.values());

        ResourceDistributionResult.TextRuntime text =
                (ResourceDistributionResult.TextRuntime) entitlement(api, "api.tier").grants().getFirst().runtime();
        assertEquals("premium", text.value());

        ResourceDistributionResult.TimeRangeRuntime timeRange =
                (ResourceDistributionResult.TimeRangeRuntime) entitlement(api, "api.accessWindow").grants().getFirst().runtime();
        assertTrue(timeRange.active());
        assertTrue(timeRange.timeRemaining().compareTo(Duration.ZERO) > 0);
    }

    @Test
    void timeRangeInactiveWhenOutsideWindow() {
        clock.setInstant(Instant.parse("2026-09-02T00:00:00Z"));
        ResourceDistributionResult.TimeRangeRuntime timeRange =
                (ResourceDistributionResult.TimeRangeRuntime) entitlement(
                        distributionService.distribute("acme", "api", "engineering"), "api.accessWindow")
                        .grants().getFirst().runtime();
        assertFalse(timeRange.active());
        assertEquals(Duration.ZERO, timeRange.timeRemaining());
    }

    @Test
    void exactGrantLookupUsesGrantIndex() {
        Optional<EntitlementGrant> found = tenant.findGrant(
                new Target(TargetType.SCOPE, "engineering"), "gpu", "gpu.hours");
        assertTrue(found.isPresent());
        assertEquals("g-eng-hours", found.get().id());
        assertTrue(tenant.isGrantIndexed(GrantLookupKey.from(found.get())));
        assertEquals(tenant.getGrants().size(), tenant.grantIndexSize());
    }

    @Test
    void entitlementResolverStillObeysNearestWinsWithIndex() {
        assertEquals("g-backend-hours", resolver.resolve(tenant, "alice", "gpu", "gpu.hours").orElseThrow().grant().id());
        assertEquals("g-eng-hours", resolver.resolve(tenant, "charlie", "gpu", "gpu.hours").orElseThrow().grant().id());
        assertEquals("g-david-hours", resolver.resolve(tenant, "david", "gpu", "gpu.hours").orElseThrow().grant().id());
    }

    @Test
    void grantDeletionCleansIndexUsageAndFallsBack() throws Exception {
        usageStore.put("g-backend-hours", new Usage(
                "g-backend-hours", BigDecimal.TEN,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));

        execute("""
                {"target":{"type":"SCOPE","id":"backend"},"resourceId":"gpu","entitlementKey":"gpu.hours"}
                """, CommandType.REMOVE_ENTITLEMENT);

        assertFalse(tenant.getGrants().containsKey("g-backend-hours"));
        assertFalse(tenant.isGrantIndexed(new GrantLookupKey(TargetType.SCOPE, "backend", "gpu", "gpu.hours")));
        assertNull(usageStore.get("g-backend-hours"));

        ResourceDistributionResult.GrantDistribution eng = grantFor(resultHours(), "g-eng-hours");
        Set<String> ids = eng.children().stream().map(ResourceDistributionResult.Child::id).collect(Collectors.toSet());
        assertTrue(ids.contains("backend"));
        assertEquals("g-eng-hours", resolver.resolve(tenant, "alice", "gpu", "gpu.hours").orElseThrow().grant().id());
    }

    @Test
    void grantReplacementRemovesOldGrantAndUsage() throws Exception {
        usageStore.put("g-eng-hours", new Usage(
                "g-eng-hours", new BigDecimal("100"),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));

        execute("""
                {"grantId":"g-eng-hours-v2","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":9000,"unit":"hour","period":"MONTHLY"}}
                """, CommandType.SET_ENTITLEMENT);

        assertFalse(tenant.getGrants().containsKey("g-eng-hours"));
        assertNull(usageStore.get("g-eng-hours"));
        assertTrue(tenant.getGrants().containsKey("g-eng-hours-v2"));
        assertEquals("g-eng-hours-v2", grantFor(resultHours(), "g-eng-hours-v2").grantId());
    }

    @Test
    void sameGrantIdMaterialQuotaChangeResetsUsage() throws Exception {
        usageStore.put("g-eng-hours", new Usage(
                "g-eng-hours", new BigDecimal("3200"),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));

        execute("""
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":10000,"unit":"hour","period":"MONTHLY"}}
                """, CommandType.SET_ENTITLEMENT);

        assertNull(usageStore.get("g-eng-hours"));
        ResourceDistributionResult.QuotaRuntime runtime =
                (ResourceDistributionResult.QuotaRuntime) grantFor(resultHours(), "g-eng-hours").runtime();
        assertEquals(0, new BigDecimal("10000").compareTo(runtime.limit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(runtime.consumed()));
    }

    @Test
    void sameGrantIdNonMaterialQuotaUpsertPreservesUsage() throws Exception {
        usageStore.put("g-eng-hours", new Usage(
                "g-eng-hours", new BigDecimal("3200"),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));

        execute("""
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """, CommandType.SET_ENTITLEMENT);

        assertNotNull(usageStore.get("g-eng-hours"));
        assertEquals(0, new BigDecimal("3200").compareTo(usageStore.get("g-eng-hours").getConsumed()));
    }

    @Test
    void subjectDeletionCleansDirectGrantsIndexAndUsage() throws Exception {
        usageStore.put("g-david-hours", new Usage(
                "g-david-hours", BigDecimal.ONE,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));
        execute("""
                {"subjectId":"david"}
                """, CommandType.REMOVE_SUBJECT);

        assertFalse(tenant.getGrants().containsKey("g-david-hours"));
        assertFalse(tenant.isGrantIndexed(new GrantLookupKey(TargetType.SUBJECT, "david", "gpu", "gpu.hours")));
        assertNull(usageStore.get("g-david-hours"));
    }

    @Test
    void scopeDeletionCleansAffectedGrantsIndexAndUsage() throws Exception {
        usageStore.put("g-backend-hours", new Usage(
                "g-backend-hours", BigDecimal.TEN,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));
        execute("""
                {"scopeId":"backend"}
                """, CommandType.REMOVE_SCOPE);

        assertFalse(tenant.getGrants().containsKey("g-backend-hours"));
        assertFalse(tenant.isGrantIndexed(new GrantLookupKey(TargetType.SCOPE, "backend", "gpu", "gpu.hours")));
        assertNull(usageStore.get("g-backend-hours"));
    }

    @Test
    void resourceDeletionCleansAllResourceGrantsIndexAndUsage() throws Exception {
        usageStore.put("g-eng-hours", new Usage(
                "g-eng-hours", BigDecimal.ONE,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));
        execute("""
                {"resourceId":"gpu"}
                """, CommandType.REMOVE_RESOURCE);

        assertTrue(tenant.getGrants().values().stream().noneMatch(g -> g.resourceId().equals("gpu")));
        assertEquals(tenant.getGrants().size(), tenant.grantIndexSize());
        assertNull(usageStore.get("g-eng-hours"));
    }

    @Test
    void removedEntitlementDefinitionCannotLeaveDanglingGrants() {
        assertThrows(IllegalArgumentException.class, () -> execute("""
                {"resourceId":"gpu","entitlementDefinitions":[{"key":"gpu.enabled","name":"GPU Enabled","valueType":"BOOLEAN"}]}
                """, CommandType.UPDATE_RESOURCE));
        assertTrue(tenant.getGrants().containsKey("g-eng-hours"));
    }

    @Test
    void invalidGrantCreationIsRejectedBeforeMutation() {
        assertThrows(IllegalArgumentException.class, () -> execute("""
                {"grantId":"bad","target":{"type":"SCOPE","id":"missing-scope"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":1,"unit":"hour","period":"MONTHLY"}}
                """, CommandType.SET_ENTITLEMENT));
        assertFalse(tenant.getGrants().containsKey("bad"));
        assertFalse(tenant.isGrantIndexed(new GrantLookupKey(TargetType.SCOPE, "missing-scope", "gpu", "gpu.hours")));

        assertThrows(IllegalArgumentException.class, () -> execute("""
                {"grantId":"bad-type","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"BOOLEAN","value":true}}
                """, CommandType.SET_ENTITLEMENT));
        assertFalse(tenant.getGrants().containsKey("bad-type"));
    }

    @Test
    void grantResolutionCacheInvalidationStillWorks() throws Exception {
        resolver.resolve(tenant, "alice", "gpu", "gpu.hours");
        assertEquals(Optional.of("g-backend-hours"), cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")));

        execute("""
                {"target":{"type":"SCOPE","id":"backend"},"resourceId":"gpu","entitlementKey":"gpu.hours"}
                """, CommandType.REMOVE_ENTITLEMENT);

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")).isEmpty());
        assertEquals("g-eng-hours", resolver.resolve(tenant, "alice", "gpu", "gpu.hours").orElseThrow().grant().id());
    }

    @Test
    void distributionDoesNotMutateUsageWhenViewed() {
        assertNull(usageStore.get("g-eng-hours"));
        distributionService.distribute("acme", "gpu", "engineering");
        assertNull(usageStore.get("g-eng-hours"));
        usageService.consume(new ConsumptionRequest("acme", "charlie", "gpu", "gpu.hours", new BigDecimal("5")));
        ResourceDistributionResult.QuotaRuntime runtime =
                (ResourceDistributionResult.QuotaRuntime) grantFor(resultHours(), "g-eng-hours").runtime();
        assertEquals(0, new BigDecimal("5").compareTo(runtime.consumed()));
    }

    @Test
    void largeImmediateChildSetGroupsSharedGrantsWithoutDuplicates() throws Exception {
        for (int i = 0; i < 40; i++) {
            String id = "team-" + i;
            execute("{\"parentScopeId\":\"engineering\",\"scope\":{\"id\":\"" + id + "\",\"kind\":\"team\",\"name\":\"" + id + "\"}}",
                    CommandType.ADD_SCOPE);
            if (i % 5 == 0) {
                tenant.putGrant(new EntitlementGrant(
                        "override-" + id,
                        new Target(TargetType.SCOPE, id),
                        "gpu",
                        "gpu.hours",
                        new QuotaValue(new BigDecimal("50"), "hour", QuotaPeriod.MONTHLY)));
            }
        }

        ResourceDistributionResult.EntitlementDistribution hours = resultHours();
        Set<String> grantIds = hours.grants().stream().map(ResourceDistributionResult.GrantDistribution::grantId).collect(Collectors.toSet());
        assertEquals(hours.grants().size(), grantIds.size());

        long sharedChildren = grantFor(hours, "g-eng-hours").children().stream()
                .filter(c -> c.id().startsWith("team-"))
                .count();
        assertEquals(32, sharedChildren);
    }

    @Test
    void grantsAndIndexStaySynchronizedAfterCommands() throws Exception {
        assertEquals(tenant.getGrants().size(), tenant.grantIndexSize());
        execute("""
                {"grantId":"g-ml-hours","target":{"type":"SCOPE","id":"ml"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":10,"unit":"hour","period":"MONTHLY"}}
                """, CommandType.SET_ENTITLEMENT);
        assertEquals(tenant.getGrants().size(), tenant.grantIndexSize());
        execute("""
                {"target":{"type":"SCOPE","id":"ml"},"resourceId":"gpu","entitlementKey":"gpu.hours"}
                """, CommandType.REMOVE_ENTITLEMENT);
        assertEquals(tenant.getGrants().size(), tenant.grantIndexSize());
    }

    @Test
    void rateLimitRuntimeIsExposedWhenPresent() {
        ResourceDistributionResult api = distributionService.distribute("acme", "api", "engineering");
        ResourceDistributionResult.RateLimitRuntime runtime =
                (ResourceDistributionResult.RateLimitRuntime) entitlement(api, "api.rateLimit").grants().getFirst().runtime();
        assertEquals(0, new BigDecimal("100").compareTo(runtime.capacity()));
        assertEquals(0, new BigDecimal("100").compareTo(runtime.availableTokens()));
    }

    private ResourceDistributionResult.EntitlementDistribution resultHours() {
        return entitlement(distributionService.distribute("acme", "gpu", "engineering"), "gpu.hours");
    }

    private static ResourceDistributionResult.EntitlementDistribution entitlement(
            ResourceDistributionResult result, String key) {
        return result.entitlements().stream()
                .filter(e -> e.entitlementKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static ResourceDistributionResult.GrantDistribution grantFor(
            ResourceDistributionResult.EntitlementDistribution entitlement, String grantId) {
        return entitlement.grants().stream()
                .filter(g -> g.grantId().equals(grantId))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> childIdsFor(ResourceDistributionResult result, String entitlementKey) {
        return entitlement(result, entitlementKey).grants().stream()
                .flatMap(g -> g.children().stream())
                .map(ResourceDistributionResult.Child::id)
                .collect(Collectors.toSet());
    }

    private void execute(String payload, CommandType type) throws Exception {
        JsonNode node = mapper.readTree(payload);
        commandService.execute(new CommandRequest(type, "acme", node));
    }
}
