package com.example.entitlements.cache;

import com.example.entitlements.domain.Tenant;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionCacheInvalidatorTest {
    private GrantResolutionCache cache;
    private ResolutionCacheInvalidator invalidator;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        cache = new GrantResolutionCache();
        invalidator = new ResolutionCacheInvalidator(cache);
        tenant = TestFixtures.registeredTenant(new TenantRegistry());

        cache.put(new ResolutionKey("acme", "alice", "api", "api.requests"), "g-eng-quota");
        cache.put(new ResolutionKey("acme", "bob", "api", "api.requests"), "g-eng-quota");
        cache.put(new ResolutionKey("acme", "charlie", "api", "api.requests"), "g-eng-quota");
        cache.put(new ResolutionKey("acme", "eve", "api", "api.requests"), "g-root-quota");
        cache.put(new ResolutionKey("acme", "alice", "gpu", "gpu.hours"), "g-gpu");
    }

    @Test
    void invalidateTenantClearsOnlyThatTenant() {
        cache.put(new ResolutionKey("other", "alice", "api", "api.requests"), "g-other");
        invalidator.invalidateTenant("acme");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "bob", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")).isEmpty());
        assertEquals(Optional.of("g-other"), cache.get(new ResolutionKey("other", "alice", "api", "api.requests")));
    }

    @Test
    void invalidateScopeSubtreeInvalidatesAllSubjectsInSubtree() {
        invalidator.invalidateScopeSubtree(tenant, "engineering");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "bob", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "charlie", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "eve", "api", "api.requests")).isPresent());
    }

    @Test
    void invalidateScopeEntitlementOnlyClearsMatchingResourceAndKey() {
        invalidator.invalidateScopeEntitlement(tenant, "engineering", "api", "api.requests");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "bob", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "charlie", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")).isPresent());
        assertTrue(cache.get(new ResolutionKey("acme", "eve", "api", "api.requests")).isPresent());
    }

    @Test
    void changingOneBranchDoesNotInvalidateUnrelatedSubjects() {
        invalidator.invalidateScopeSubtree(tenant, "marketing");

        assertTrue(cache.get(new ResolutionKey("acme", "eve", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isPresent());
        assertTrue(cache.get(new ResolutionKey("acme", "charlie", "api", "api.requests")).isPresent());
    }

    @Test
    void bulkScopeInvalidationVisitsSubtreeOnceAndIsNoOpWhenEmpty() {
        int before = cache.size();
        invalidator.invalidateScopeSubtree(tenant, "backend");
        assertEquals(before - 3, cache.size()); // alice api, bob api, alice gpu

        invalidator.invalidateScopeSubtree(tenant, "backend");
        assertEquals(before - 3, cache.size());
    }
}
