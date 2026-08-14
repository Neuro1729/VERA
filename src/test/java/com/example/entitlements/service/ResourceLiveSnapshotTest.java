package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
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
import java.time.Instant;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ResourceLiveSnapshotTest {
    private TenantRegistry registry;
    private UsageStore usageStore;
    private Tenant tenant;
    private MutableClock clock;
    private ResourceDistributionService distributionService;
    private CommandService commandService;
    private UsageService usageService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        registry = new TenantRegistry();
        usageStore = new UsageStore();
        tenant = TestFixtures.registeredTenant(registry);
        clock = new MutableClock(Instant.parse("2026-08-14T12:00:00Z"));
        GrantResolutionCache cache = new GrantResolutionCache();
        EntitlementResolver resolver = new EntitlementResolver(cache);
        RateLimitService rateLimitService = new RateLimitService(registry, resolver, new UsageHistoryStore(), clock);
        distributionService = new ResourceDistributionService(registry, usageStore, rateLimitService, clock);
        mapper = new ObjectMapper().findAndRegisterModules();
        commandService = new CommandService(
                registry, usageStore, mapper, cache, new ResolutionCacheInvalidator(cache), rateLimitService,
                new EntitlementHistoryService(registry, new EntitlementHistoryStore(), clock));
        usageService = new UsageService(registry, usageStore, resolver, new UsageHistoryStore(), clock);
        seedGpuLiveFixture();
    }

    private void seedGpuLiveFixture() throws Exception {
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
    void countsSubjectsCurrentlyResolvingToEachGrant() {
        ResourceLiveResult.GrantLive engHours = grant(liveGpu(), "gpu.hours", "g-eng-hours");
        ResourceLiveResult.GrantLive backendHours = grant(liveGpu(), "gpu.hours", "g-backend-hours");
        ResourceLiveResult.GrantLive davidHours = grant(liveGpu(), "gpu.hours", "g-david-hours");

        assertEquals(1, engHours.entitledSubjectCount());
        assertEquals(2, backendHours.entitledSubjectCount());
        assertEquals(1, davidHours.entitledSubjectCount());
        assertTrue(engHours.active());
        assertTrue(backendHours.active());
        assertTrue(davidHours.active());
    }

    @Test
    void booleanGrantCountsAllInheritingSubjects() {
        ResourceLiveResult.GrantLive enabled = grant(liveGpu(), "gpu.enabled", "g-eng-enabled");
        assertEquals(4, enabled.entitledSubjectCount());
        assertTrue(enabled.active());
    }

    @Test
    void grantWithNoSubjectsStillAppearsWithZeroCount() {
        tenant.putGrant(new EntitlementGrant(
                "g-platform-hours",
                new Target(TargetType.SCOPE, "platform"),
                "gpu",
                "gpu.hours",
                new QuotaValue(new BigDecimal("50"), "hour", QuotaPeriod.MONTHLY)));

        ResourceLiveResult.GrantLive platform = grant(liveGpu(), "gpu.hours", "g-platform-hours");
        assertEquals(0, platform.entitledSubjectCount());
        assertEquals(1, grant(liveGpu(), "gpu.hours", "g-eng-hours").entitledSubjectCount());
    }

    @Test
    void quotaRuntimeIsCurrentAndReadOnly() {
        usageStore.put("g-eng-hours", new Usage(
                "g-eng-hours",
                new BigDecimal("3200"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")));

        ResourceDistributionResult.QuotaRuntime quota =
                (ResourceDistributionResult.QuotaRuntime) grant(liveGpu(), "gpu.hours", "g-eng-hours").runtime();
        assertEquals(0, new BigDecimal("3200").compareTo(quota.consumed()));
        assertEquals(0, new BigDecimal("1800").compareTo(quota.remaining()));
        assertEquals(0, new BigDecimal("3200").compareTo(usageStore.get("g-eng-hours").getConsumed()));
    }

    @Test
    void liveDoesNotCreateUsageEntries() {
        assertNull(usageStore.get("g-david-hours"));
        liveGpu();
        assertNull(usageStore.get("g-david-hours"));
    }

    @Test
    void exhaustedQuotaIsNotActive() {
        usageService.consume(new ConsumptionRequest("acme", "david", "gpu", "gpu.hours", new BigDecimal("1000")));
        ResourceLiveResult.GrantLive david = grant(liveGpu(), "gpu.hours", "g-david-hours");
        ResourceDistributionResult.QuotaRuntime runtime = (ResourceDistributionResult.QuotaRuntime) david.runtime();
        assertEquals(0, BigDecimal.ZERO.compareTo(runtime.remaining()));
        assertFalse(david.active());
    }

    @Test
    void timeRangeActiveFollowsClock() {
        ResourceLiveResult.GrantLive window = grant(
                distributionService.live("acme", "api"), "api.accessWindow", "g-window");
        assertTrue(window.active());
        assertEquals(4, window.entitledSubjectCount());

        clock.setInstant(Instant.parse("2026-09-02T00:00:00Z"));
        ResourceLiveResult.GrantLive expired = grant(
                distributionService.live("acme", "api"), "api.accessWindow", "g-window");
        assertFalse(expired.active());
        assertEquals(4, expired.entitledSubjectCount());
    }

    @Test
    void liveUpdatesAfterConsumptionWithoutMutatingOnRead() {
        usageService.consume(new ConsumptionRequest("acme", "charlie", "gpu", "gpu.hours", new BigDecimal("25")));
        ResourceDistributionResult.QuotaRuntime first =
                (ResourceDistributionResult.QuotaRuntime) grant(liveGpu(), "gpu.hours", "g-eng-hours").runtime();
        assertEquals(0, new BigDecimal("25").compareTo(first.consumed()));

        ResourceDistributionResult.QuotaRuntime second =
                (ResourceDistributionResult.QuotaRuntime) grant(liveGpu(), "gpu.hours", "g-eng-hours").runtime();
        assertEquals(0, new BigDecimal("25").compareTo(second.consumed()));
    }

    @Test
    void missingTenantOrResourceIsNotFound() {
        assertThrows(NoSuchElementException.class, () -> distributionService.live("missing", "gpu"));
        assertThrows(NoSuchElementException.class, () -> distributionService.live("acme", "missing"));
    }

    private ResourceLiveResult liveGpu() {
        return distributionService.live("acme", "gpu");
    }

    private static ResourceLiveResult.GrantLive grant(ResourceLiveResult result, String key, String grantId) {
        return result.entitlements().stream()
                .filter(e -> e.entitlementKey().equals(key))
                .flatMap(e -> e.grants().stream())
                .filter(g -> g.grantId().equals(grantId))
                .findFirst()
                .orElseThrow();
    }

    private void execute(String payload, CommandType type) throws Exception {
        JsonNode node = mapper.readTree(payload);
        commandService.execute(new CommandRequest(type, "acme", node));
    }
}
