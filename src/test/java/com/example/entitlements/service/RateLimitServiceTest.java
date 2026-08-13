package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.cache.ResolutionKey;
import com.example.entitlements.domain.*;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.request.RateLimitRequest;
import com.example.entitlements.request.RateLimitResult;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.MutableClock;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {
    private TenantRegistry registry;
    private Tenant tenant;
    private MutableClock clock;
    private GrantResolutionCache cache;
    private RateLimitService rateLimitService;
    private CommandService commandService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
        tenant = TestFixtures.registeredTenant(registry);
        clock = new MutableClock(Instant.parse("2026-08-12T12:00:00Z"));
        cache = new GrantResolutionCache();
        EntitlementResolver resolver = new EntitlementResolver(cache);
        rateLimitService = new RateLimitService(registry, resolver, clock);
        commandService = new CommandService(
                registry,
                new UsageStore(),
                new ObjectMapper().findAndRegisterModules(),
                cache,
                new ResolutionCacheInvalidator(cache),
                rateLimitService);
        mapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void newBucketStartsFull() {
        RateLimitResult result = consume("alice", "1");
        assertTrue(result.allowed());
        assertEquals("g-eng-rate", result.grantId());
        assertEquals(0, new BigDecimal("99").compareTo(result.availableTokens()));
    }

    @Test
    void successfulRequestConsumesTokensAndMultipleTokenConsumptionWorks() {
        RateLimitResult first = consume("alice", "30");
        RateLimitResult second = consume("alice", "20");
        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertEquals(0, new BigDecimal("50").compareTo(second.availableTokens()));
    }

    @Test
    void insufficientTokensAreRejectedWithoutDeduction() {
        consume("alice", "90");
        RateLimitResult denied = consume("alice", "20");
        assertFalse(denied.allowed());
        assertEquals("rate limit exceeded", denied.reason());
        assertEquals(0, new BigDecimal("10").compareTo(denied.availableTokens()));

        RateLimitResult stillHasTen = consume("alice", "10");
        assertTrue(stillHasTen.allowed());
        assertEquals(0, BigDecimal.ZERO.compareTo(stillHasTen.availableTokens()));
    }

    @Test
    void tokensRefillAccordingToElapsedTimeAndNeverExceedCapacity() {
        consume("alice", "80");
        clock.setInstant(Instant.parse("2026-08-12T12:00:30Z"));

        RateLimitResult afterHalfMinute = consume("alice", "1");
        assertTrue(afterHalfMinute.allowed());
        // 20 remaining + 50 refilled - 1 = 69
        assertEquals(0, new BigDecimal("69").compareTo(afterHalfMinute.availableTokens()));

        clock.setInstant(Instant.parse("2026-08-12T12:10:00Z"));
        RateLimitResult capped = consume("alice", "1");
        assertTrue(capped.allowed());
        assertEquals(0, new BigDecimal("99").compareTo(capped.availableTokens()));
    }

    @Test
    void fractionalRefillWorks() {
        consume("alice", "100");
        clock.setInstant(Instant.parse("2026-08-12T12:00:06Z")); // 10% of minute => 10 tokens
        RateLimitResult result = consume("alice", "1");
        assertTrue(result.allowed());
        assertEquals(0, new BigDecimal("9").compareTo(result.availableTokens()));
    }

    @Test
    void subjectsInheritingSameGrantShareOneBucket() {
        consume("alice", "30");
        RateLimitResult bob = consume("bob", "20");
        assertEquals("g-eng-rate", bob.grantId());
        assertEquals(0, new BigDecimal("50").compareTo(bob.availableTokens()));
        assertEquals(1, rateLimitService.bucketCount());
    }

    @Test
    void subjectOverrideGetsSeparateBucket() {
        tenant.putGrant(new EntitlementGrant(
                "g-alice-rate",
                new Target(TargetType.SUBJECT, "alice"),
                "api",
                "api.rateLimit",
                new RateLimitValue(new BigDecimal("500"), new BigDecimal("500"), Duration.ofMinutes(1))));

        consume("bob", "40");
        RateLimitResult alice = consume("alice", "10");
        assertEquals("g-alice-rate", alice.grantId());
        assertEquals(0, new BigDecimal("490").compareTo(alice.availableTokens()));
        assertTrue(rateLimitService.hasBucket("acme", "g-eng-rate"));
        assertTrue(rateLimitService.hasBucket("acme", "g-alice-rate"));
    }

    @Test
    void parentFallbackWorksForSubjectsWithoutCloserGrant() {
        RateLimitResult eve = consume("eve", "5");
        assertEquals("g-root-rate", eve.grantId());
        assertEquals(0, new BigDecimal("15").compareTo(eve.availableTokens()));
    }

    @Test
    void resolutionCacheIsUsedThroughEntitlementResolver() {
        consume("alice", "1");
        assertEquals("g-eng-rate", cache.get(new ResolutionKey("acme", "alice", "api", "api.rateLimit")).orElseThrow());

        tenant.putGrant(new EntitlementGrant(
                "g-backend-rate-hidden",
                new Target(TargetType.SCOPE, "backend"),
                "api",
                "api.rateLimit",
                new RateLimitValue(new BigDecimal("1"), new BigDecimal("1"), Duration.ofMinutes(1))));

        RateLimitResult cached = consume("alice", "1");
        assertEquals("g-eng-rate", cached.grantId());
    }

    @Test
    void nearerEntitlementInvalidatesOldResolutionButKeepsSharedOldBucket() throws Exception {
        consume("alice", "30");
        consume("bob", "10");
        assertTrue(rateLimitService.hasBucket("acme", "g-eng-rate"));

        commandService.execute(new CommandRequest(
                CommandType.SET_ENTITLEMENT,
                "acme",
                mapper.readTree("""
                        {
                          "grantId":"g-alice-rate",
                          "target":{"type":"SUBJECT","id":"alice"},
                          "resourceId":"api",
                          "entitlementKey":"api.rateLimit",
                          "value":{"type":"RATE_LIMIT","capacity":500,"refillTokens":500,"refillPeriod":"PT1M"}
                        }
                        """)));

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.rateLimit")).isEmpty());
        assertTrue(rateLimitService.hasBucket("acme", "g-eng-rate"));

        RateLimitResult alice = consume("alice", "1");
        assertEquals("g-alice-rate", alice.grantId());
        assertEquals(0, new BigDecimal("499").compareTo(alice.availableTokens()));

        RateLimitResult bob = consume("bob", "1");
        assertEquals("g-eng-rate", bob.grantId());
        assertEquals(0, new BigDecimal("59").compareTo(bob.availableTokens()));
    }

    @Test
    void deletingRateLimitGrantDeletesItsBucket() throws Exception {
        consume("alice", "1");
        assertTrue(rateLimitService.hasBucket("acme", "g-eng-rate"));

        commandService.execute(new CommandRequest(
                CommandType.REMOVE_ENTITLEMENT,
                "acme",
                mapper.readTree("""
                        {"target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.rateLimit"}
                        """)));

        assertFalse(rateLimitService.hasBucket("acme", "g-eng-rate"));
        RateLimitResult alice = consume("alice", "1");
        assertEquals("g-root-rate", alice.grantId());
    }

    @Test
    void updatingRateLimitGrantResetsOnlyThatBucket() throws Exception {
        consume("alice", "40");
        consume("eve", "5");
        assertTrue(rateLimitService.hasBucket("acme", "g-eng-rate"));
        assertTrue(rateLimitService.hasBucket("acme", "g-root-rate"));

        commandService.execute(new CommandRequest(
                CommandType.SET_ENTITLEMENT,
                "acme",
                mapper.readTree("""
                        {
                          "grantId":"g-eng-rate-v2",
                          "target":{"type":"SCOPE","id":"engineering"},
                          "resourceId":"api",
                          "entitlementKey":"api.rateLimit",
                          "value":{"type":"RATE_LIMIT","capacity":10,"refillTokens":10,"refillPeriod":"PT1M"}
                        }
                        """)));

        assertFalse(rateLimitService.hasBucket("acme", "g-eng-rate"));
        assertTrue(rateLimitService.hasBucket("acme", "g-root-rate"));

        RateLimitResult alice = consume("alice", "1");
        assertEquals("g-eng-rate-v2", alice.grantId());
        assertEquals(0, new BigDecimal("9").compareTo(alice.availableTokens()));
    }

    @Test
    void differentTenantsAndGrantsDoNotShareBuckets() {
        consume("alice", "1");
        consume("eve", "1");
        assertTrue(rateLimitService.hasBucket("acme", "g-eng-rate"));
        assertTrue(rateLimitService.hasBucket("acme", "g-root-rate"));
        assertFalse(rateLimitService.hasBucket("other", "g-eng-rate"));
        assertEquals(2, rateLimitService.bucketCount());
    }

    @Test
    void concurrentRequestsCannotOverspendABucket() throws Exception {
        tenant.putGrant(new EntitlementGrant(
                "g-tiny-rate",
                new Target(TargetType.SUBJECT, "alice"),
                "api",
                "api.rateLimit",
                new RateLimitValue(new BigDecimal("10"), new BigDecimal("10"), Duration.ofMinutes(1))));
        cache.invalidateSubject("acme", "alice");

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                RateLimitResult result = rateLimitService.tryConsume(
                        new RateLimitRequest("acme", "alice", "api", "api.rateLimit", BigDecimal.ONE));
                if (result.allowed()) allowed.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) future.get();
        pool.shutdownNow();
        assertEquals(10, allowed.get());
    }

    @Test
    void existingQuotaBehaviorRemainsUnchanged() {
        UsageService usageService = new UsageService(
                registry, new UsageStore(), new EntitlementResolver(cache), clock);
        var result = usageService.consume(new com.example.entitlements.request.ConsumptionRequest(
                "acme", "alice", "api", "api.requests", new BigDecimal("100")));
        assertTrue(result.allowed());
        assertEquals("g-eng-quota", result.grantId());
        assertEquals(0, new BigDecimal("999900").compareTo(result.remaining()));
    }

    private RateLimitResult consume(String subjectId, String tokens) {
        return rateLimitService.tryConsume(
                new RateLimitRequest("acme", subjectId, "api", "api.rateLimit", new BigDecimal(tokens)));
    }
}
