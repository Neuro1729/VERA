package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.ConsumptionResult;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.MutableClock;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UsageServiceTest {
    private TenantRegistry registry;
    private UsageStore usageStore;
    private Tenant tenant;
    private MutableClock clock;
    private UsageService service;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
        usageStore = new UsageStore();
        tenant = TestFixtures.registeredTenant(registry);
        clock = new MutableClock(Instant.parse("2026-08-12T12:00:00Z"));
        service = new UsageService(registry, usageStore, new EntitlementResolver(), clock);
    }

    @Test
    void usersResolvingToSameDepartmentGrantShareOneQuotaPool() {
        ConsumptionResult alice = service.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("100")));
        ConsumptionResult bob = service.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("250")));

        assertTrue(alice.allowed());
        assertTrue(bob.allowed());
        assertEquals("g-eng-quota", alice.grantId());
        assertEquals("g-eng-quota", bob.grantId());
        assertEquals(new BigDecimal("350"), bob.consumed());
        assertEquals(new BigDecimal("999650"), bob.remaining());
    }

    @Test
    void subjectOverrideUsesItsOwnUsagePool() {
        EntitlementGrant personal = new EntitlementGrant("g-alice-personal", new Target(TargetType.SUBJECT, "alice"), "api", "api.requests",
                new QuotaValue(new BigDecimal("500"), "request", QuotaPeriod.MONTHLY));
        tenant.getGrants().put(personal.id(), personal);

        ConsumptionResult alice = service.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("100")));
        ConsumptionResult bob = service.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("100")));

        assertEquals("g-alice-personal", alice.grantId());
        assertEquals(new BigDecimal("400"), alice.remaining());
        assertEquals("g-eng-quota", bob.grantId());
        assertEquals(new BigDecimal("999900"), bob.remaining());
    }

    @Test
    void requestThatWouldExceedQuotaIsRejectedWithoutIncrementingUsage() {
        ConsumptionResult rejected = service.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("1000001")));

        assertFalse(rejected.allowed());
        assertEquals(BigDecimal.ZERO, rejected.consumed());
        assertEquals(new BigDecimal("1000000"), rejected.remaining());
    }

    @Test
    void exactRemainingQuotaCanBeConsumed() {
        service.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("999999")));
        ConsumptionResult result = service.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", BigDecimal.ONE));

        assertTrue(result.allowed());
        assertEquals(BigDecimal.ZERO, result.remaining());
    }

    @Test
    void monthlyQuotaResetsWhenClockMovesIntoNextMonth() {
        service.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("700")));
        clock.setInstant(Instant.parse("2026-09-01T00:00:01Z"));

        ConsumptionResult result = service.consume(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("10")));

        assertEquals(new BigDecimal("10"), result.consumed());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), result.periodStart());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), result.periodEnd());
    }

    @Test
    void yearlyQuotaUsesCalendarYearBoundaries() {
        EntitlementGrant yearly = new EntitlementGrant("yearly", new Target(TargetType.SUBJECT, "alice"), "gpu", "gpu.hours",
                new QuotaValue(new BigDecimal("100"), "hour", QuotaPeriod.YEARLY));
        tenant.getGrants().put(yearly.id(), yearly);

        ConsumptionResult first = service.consume(new ConsumptionRequest("acme", "alice", "gpu", "gpu.hours", new BigDecimal("90")));
        clock.setInstant(Instant.parse("2027-01-01T00:00:00Z"));
        ConsumptionResult nextYear = service.consume(new ConsumptionRequest("acme", "alice", "gpu", "gpu.hours", new BigDecimal("5")));

        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), first.periodStart());
        assertEquals(new BigDecimal("5"), nextYear.consumed());
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), nextYear.periodStart());
    }

    @Test
    void rootScopeQuotaIsSharedBySubjectsWithoutCloserOverride() {
        ConsumptionResult eve = service.consume(new ConsumptionRequest("acme", "eve", "api", "api.requests", new BigDecimal("1000")));
        assertEquals("g-root-quota", eve.grantId());
        assertEquals(new BigDecimal("99000"), eve.remaining());
    }

    @Test
    void nonQuotaEntitlementCannotBeConsumed() {
        assertThrows(IllegalArgumentException.class,
                () -> service.consume(new ConsumptionRequest("acme", "alice", "api", "api.enabled", BigDecimal.ONE)));
    }

    @Test
    void consumptionAmountMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> service.consume(new ConsumptionRequest("acme", "alice", "api", "api.requests", BigDecimal.ZERO)));
    }

    @Test
    void missingEntitlementCannotBeConsumed() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.consume(new ConsumptionRequest("acme", "alice", "gpu", "gpu.hours", BigDecimal.ONE)));
    }
}
