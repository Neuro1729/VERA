package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.EvaluationResult;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.MutableClock;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EntitlementServiceTest {
    private ObjectMapper mapper;
    private EntitlementService service;
    private UsageService usageService;

    @BeforeEach
    void setUp() {
        TenantRegistry registry = new TenantRegistry();
        TestFixtures.registeredTenant(registry);
        UsageStore usageStore = new UsageStore();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-12T12:00:00Z"));
        EntitlementResolver resolver = new EntitlementResolver(new GrantResolutionCache());
        usageService = new UsageService(registry, usageStore, resolver, clock);
        RateLimitService rateLimitService = new RateLimitService(registry, resolver, clock);
        service = new EntitlementService(registry, resolver, usageService, rateLimitService, clock);
        mapper = new ObjectMapper();
    }

    @Test
    void evaluatesInheritedBoolean() {
        EvaluationResult result = service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.enabled", null));
        assertTrue(result.allowed());
        assertEquals("g-root-enabled", result.grantId());
    }

    @Test
    void quantityAllowsValueAtOrBelowLimit() {
        assertTrue(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.maxBatch", mapper.valueToTree(100))).allowed());
        assertTrue(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.maxBatch", mapper.valueToTree(25))).allowed());
    }

    @Test
    void quantityRejectsValueAboveLimit() {
        assertFalse(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.maxBatch", mapper.valueToTree(101))).allowed());
    }

    @Test
    void rangeAcceptsInsideAndRejectsOutside() {
        assertTrue(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.temperature", mapper.valueToTree(1.5))).allowed());
        assertFalse(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.temperature", mapper.valueToTree(2.1))).allowed());
    }

    @Test
    void setAllowsSingleMember() {
        assertTrue(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("large"))).allowed());
        assertFalse(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("unknown"))).allowed());
    }

    @Test
    void setAllowsSubsetArrayAndRejectsUnknownMember() {
        ArrayNode allowed = mapper.createArrayNode().add("small").add("large");
        ArrayNode denied = mapper.createArrayNode().add("small").add("other");
        assertTrue(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.models", allowed)).allowed());
        assertFalse(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.models", denied)).allowed());
    }

    @Test
    void textRequiresExactMatch() {
        assertTrue(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.tier", mapper.valueToTree("premium"))).allowed());
        assertFalse(service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.tier", mapper.valueToTree("basic"))).allowed());
    }

    @Test
    void timeRangeIsEvaluatedFromClockWithoutBackgroundJob() {
        EvaluationResult result = service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.accessWindow", null));
        assertTrue(result.allowed());
    }

    @Test
    void quotaEvaluationReportsRemainingPool() {
        EvaluationResult result = service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(1000)));
        assertTrue(result.allowed());
        assertEquals(0, result.remaining().compareTo(new java.math.BigDecimal("1000000")));
    }

    @Test
    void invalidRequestedTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(new EvaluationRequest("acme", "alice", "api", "api.maxBatch", mapper.valueToTree("not-a-number"))));
    }
}
