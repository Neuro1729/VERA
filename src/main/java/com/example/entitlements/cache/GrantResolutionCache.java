package com.example.entitlements.cache;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class GrantResolutionCache {
    private final ConcurrentMap<ResolutionKey, String> cache = new ConcurrentHashMap<>();

    public Optional<String> get(ResolutionKey key) {
        Objects.requireNonNull(key, "key is required");
        return Optional.ofNullable(cache.get(key));
    }

    public void put(ResolutionKey key, String grantId) {
        Objects.requireNonNull(key, "key is required");
        if (grantId == null || grantId.isBlank()) {
            throw new IllegalArgumentException("grantId is required");
        }
        cache.put(key, grantId);
    }

    public void remove(ResolutionKey key) {
        Objects.requireNonNull(key, "key is required");
        cache.remove(key);
    }

    public void invalidateSubject(String tenantId, String subjectId) {
        requireId(tenantId, "tenantId");
        requireId(subjectId, "subjectId");
        cache.keySet().removeIf(key ->
                key.tenantId().equals(tenantId) && key.subjectId().equals(subjectId));
    }

    public void invalidateSubjectEntitlement(
            String tenantId,
            String subjectId,
            String resourceId,
            String entitlementKey
    ) {
        remove(new ResolutionKey(tenantId, subjectId, resourceId, entitlementKey));
    }

    public void invalidateResource(String tenantId, String resourceId) {
        requireId(tenantId, "tenantId");
        requireId(resourceId, "resourceId");
        cache.keySet().removeIf(key ->
                key.tenantId().equals(tenantId) && key.resourceId().equals(resourceId));
    }

    public void invalidateTenant(String tenantId) {
        requireId(tenantId, "tenantId");
        cache.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    public void clear() {
        cache.clear();
    }

    /** Visible for tests and lightweight monitoring. */
    public int size() {
        return cache.size();
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
