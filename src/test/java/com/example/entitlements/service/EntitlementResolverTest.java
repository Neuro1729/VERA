package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionKey;
import com.example.entitlements.domain.*;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EntitlementResolverTest {
    private Tenant tenant;
    private GrantResolutionCache cache;
    private EntitlementResolver resolver;

    @BeforeEach
    void setUp() {
        TenantRegistry registry = new TenantRegistry();
        tenant = TestFixtures.registeredTenant(registry);
        cache = new GrantResolutionCache();
        resolver = new EntitlementResolver(cache);
    }

    @Test
    void nearestScopeGrantWins() {
        ResolvedEntitlement resolved = resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow();
        assertEquals("g-eng-quota", resolved.grant().id());
        assertEquals(new Target(TargetType.SCOPE, "engineering"), resolved.source());
    }

    @Test
    void fallsBackToRootScopeWhenNoCloserGrantExists() {
        ResolvedEntitlement resolved = resolver.resolve(tenant, "eve", "api", "api.requests").orElseThrow();
        assertEquals("g-root-quota", resolved.grant().id());
    }

    @Test
    void subjectGrantWinsOverEveryScopeGrant() {
        EntitlementGrant personal = new EntitlementGrant("g-alice", new Target(TargetType.SUBJECT, "alice"), "api", "api.requests",
                new QuotaValue(new BigDecimal("9000000"), "request", QuotaPeriod.MONTHLY));
        tenant.putGrant(personal);

        ResolvedEntitlement resolved = resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow();
        assertEquals("g-alice", resolved.grant().id());
    }

    @Test
    void childScopeGrantWinsOverParentScopeGrant() {
        EntitlementGrant backend = new EntitlementGrant("g-backend", new Target(TargetType.SCOPE, "backend"), "api", "api.requests",
                new QuotaValue(new BigDecimal("2000000"), "request", QuotaPeriod.MONTHLY));
        tenant.putGrant(backend);

        assertEquals("g-backend", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
        assertEquals("g-eng-quota", resolver.resolve(tenant, "charlie", "api", "api.requests").orElseThrow().grant().id());
    }

    @Test
    void resolvesDifferentEntitlementsIndependently() {
        assertEquals("g-batch", resolver.resolve(tenant, "alice", "api", "api.maxBatch").orElseThrow().grant().id());
        assertEquals("g-root-enabled", resolver.resolve(tenant, "alice", "api", "api.enabled").orElseThrow().grant().id());
    }

    @Test
    void returnsEmptyWhenNoGrantExists() {
        Optional<ResolvedEntitlement> result = resolver.resolve(tenant, "alice", "gpu", "gpu.enabled");
        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsUnknownSubject() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> resolver.resolve(tenant, "missing", "api", "api.requests"));
    }

    @Test
    void rejectsUnknownResource() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> resolver.resolve(tenant, "alice", "missing", "api.requests"));
    }

    @Test
    void firstResolutionIsCacheMissAndSecondUsesCachedGrantId() {
        ResolutionKey key = new ResolutionKey("acme", "alice", "api", "api.requests");
        assertTrue(cache.get(key).isEmpty());

        ResolvedEntitlement first = resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow();
        assertEquals("g-eng-quota", first.grant().id());
        assertEquals(Optional.of("g-eng-quota"), cache.get(key));

        EntitlementGrant nearer = new EntitlementGrant(
                "g-backend-hidden",
                new Target(TargetType.SCOPE, "backend"),
                "api",
                "api.requests",
                new QuotaValue(new BigDecimal("1"), "request", QuotaPeriod.MONTHLY));
        tenant.putGrant(nearer);

        ResolvedEntitlement second = resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow();
        assertEquals("g-eng-quota", second.grant().id());
        assertSame(tenant.getGrants().get("g-eng-quota"), second.grant());
    }

    @Test
    void staleCachedGrantIdCausesReResolution() {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        tenant.removeGrant("g-eng-quota");

        ResolvedEntitlement resolved = resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow();
        assertEquals("g-root-quota", resolved.grant().id());
        assertEquals(Optional.of("g-root-quota"), cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")));
    }

    @Test
    void aliceAndBobMayCacheTheSameEngineeringGrantId() {
        assertEquals("g-eng-quota", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
        assertEquals("g-eng-quota", resolver.resolve(tenant, "bob", "api", "api.requests").orElseThrow().grant().id());
        assertEquals(Optional.of("g-eng-quota"), cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")));
        assertEquals(Optional.of("g-eng-quota"), cache.get(new ResolutionKey("acme", "bob", "api", "api.requests")));
    }

    @Test
    void differentTenantsNeverCollide() {
        cache.put(new ResolutionKey("other", "alice", "api", "api.requests"), "foreign-grant");
        assertEquals("g-eng-quota", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
        assertEquals(Optional.of("foreign-grant"), cache.get(new ResolutionKey("other", "alice", "api", "api.requests")));
    }
}
