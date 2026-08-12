package com.example.entitlements.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GrantResolutionCacheTest {
    private GrantResolutionCache cache;

    @BeforeEach
    void setUp() {
        cache = new GrantResolutionCache();
    }

    @Test
    void storesOnlyGrantIdsAndSupportsIndependentKeys() {
        ResolutionKey aliceApi = new ResolutionKey("acme", "alice", "api", "api.requests");
        ResolutionKey bobApi = new ResolutionKey("acme", "bob", "api", "api.requests");
        ResolutionKey aliceGpu = new ResolutionKey("acme", "alice", "gpu", "gpu.hours");
        ResolutionKey otherTenant = new ResolutionKey("other", "alice", "api", "api.requests");

        cache.put(aliceApi, "g-eng-quota");
        cache.put(bobApi, "g-eng-quota");
        cache.put(aliceGpu, "g-gpu");
        cache.put(otherTenant, "g-other");

        assertEquals(Optional.of("g-eng-quota"), cache.get(aliceApi));
        assertEquals(Optional.of("g-eng-quota"), cache.get(bobApi));
        assertEquals(Optional.of("g-gpu"), cache.get(aliceGpu));
        assertEquals(Optional.of("g-other"), cache.get(otherTenant));
        assertEquals(4, cache.size());
    }

    @Test
    void invalidateSubjectRemovesOnlyThatSubjectsEntries() {
        cache.put(new ResolutionKey("acme", "alice", "api", "api.requests"), "g1");
        cache.put(new ResolutionKey("acme", "alice", "gpu", "gpu.hours"), "g2");
        cache.put(new ResolutionKey("acme", "bob", "api", "api.requests"), "g1");

        cache.invalidateSubject("acme", "alice");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")).isEmpty());
        assertEquals(Optional.of("g1"), cache.get(new ResolutionKey("acme", "bob", "api", "api.requests")));
    }

    @Test
    void invalidateSubjectEntitlementRemovesExactKeyOnly() {
        ResolutionKey requests = new ResolutionKey("acme", "alice", "api", "api.requests");
        ResolutionKey enabled = new ResolutionKey("acme", "alice", "api", "api.enabled");
        cache.put(requests, "g1");
        cache.put(enabled, "g2");

        cache.invalidateSubjectEntitlement("acme", "alice", "api", "api.requests");

        assertTrue(cache.get(requests).isEmpty());
        assertEquals(Optional.of("g2"), cache.get(enabled));
    }

    @Test
    void invalidateResourceRemovesMatchingTenantResourceEntries() {
        cache.put(new ResolutionKey("acme", "alice", "api", "api.requests"), "g1");
        cache.put(new ResolutionKey("acme", "bob", "api", "api.enabled"), "g2");
        cache.put(new ResolutionKey("acme", "alice", "gpu", "gpu.hours"), "g3");
        cache.put(new ResolutionKey("other", "alice", "api", "api.requests"), "g4");

        cache.invalidateResource("acme", "api");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "bob", "api", "api.enabled")).isEmpty());
        assertEquals(Optional.of("g3"), cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")));
        assertEquals(Optional.of("g4"), cache.get(new ResolutionKey("other", "alice", "api", "api.requests")));
    }

    @Test
    void invalidateTenantAndClearWorkEvenWhenNoMatchingEntriesExist() {
        cache.invalidateSubject("acme", "missing");
        cache.invalidateTenant("missing");
        assertEquals(0, cache.size());

        cache.put(new ResolutionKey("acme", "alice", "api", "api.requests"), "g1");
        cache.invalidateTenant("acme");
        assertEquals(0, cache.size());

        cache.put(new ResolutionKey("acme", "alice", "api", "api.requests"), "g1");
        cache.clear();
        assertEquals(0, cache.size());
    }
}
