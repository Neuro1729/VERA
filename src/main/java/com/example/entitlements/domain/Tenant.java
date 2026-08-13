package com.example.entitlements.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Tenant {
    private final String id;
    private String name;
    private String rootScopeId;
    private final Map<String, Scope> scopes = new LinkedHashMap<>();
    private final Map<String, Subject> subjects = new LinkedHashMap<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final Map<String, EntitlementGrant> grants = new LinkedHashMap<>();
    /** Secondary index: Target + resourceId + entitlementKey -> grantId. Does not store grant objects. */
    private final Map<GrantLookupKey, String> grantIndex = new LinkedHashMap<>();

    public Tenant(String id, String name) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("tenant id is required");
        this.id = id;
        this.name = (name == null || name.isBlank()) ? id : name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRootScopeId() { return rootScopeId; }
    public Map<String, Scope> getScopes() { return scopes; }
    public Map<String, Subject> getSubjects() { return subjects; }
    public Map<String, Resource> getResources() { return resources; }
    public Map<String, EntitlementGrant> getGrants() { return grants; }

    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public void setRootScopeId(String rootScopeId) { this.rootScopeId = rootScopeId; }

    /**
     * Inserts or replaces a grant by id, keeping {@link #grants} and {@link #grantIndex} synchronized.
     * Callers must remove any other grant for the same lookup key before calling this method.
     */
    public void putGrant(EntitlementGrant grant) {
        Objects.requireNonNull(grant, "grant is required");
        GrantLookupKey key = GrantLookupKey.from(grant);

        EntitlementGrant existingById = grants.get(grant.id());
        if (existingById != null) {
            GrantLookupKey oldKey = GrantLookupKey.from(existingById);
            String indexed = grantIndex.get(oldKey);
            if (grant.id().equals(indexed)) {
                grantIndex.remove(oldKey);
            }
        }

        String occupying = grantIndex.get(key);
        if (occupying != null && !occupying.equals(grant.id())) {
            throw new IllegalStateException(
                    "grant already indexed for target/resource/entitlement: " + occupying);
        }

        grants.put(grant.id(), grant);
        grantIndex.put(key, grant.id());
    }

    public Optional<EntitlementGrant> removeGrant(String grantId) {
        if (grantId == null || grantId.isBlank()) return Optional.empty();
        EntitlementGrant removed = grants.remove(grantId);
        if (removed == null) return Optional.empty();

        GrantLookupKey key = GrantLookupKey.from(removed);
        String indexed = grantIndex.get(key);
        if (grantId.equals(indexed)) {
            grantIndex.remove(key);
        }
        return Optional.of(removed);
    }

    public Optional<EntitlementGrant> findGrant(Target target, String resourceId, String entitlementKey) {
        GrantLookupKey key = GrantLookupKey.of(target, resourceId, entitlementKey);
        String grantId = grantIndex.get(key);
        if (grantId == null) return Optional.empty();

        EntitlementGrant grant = grants.get(grantId);
        if (grant == null) {
            grantIndex.remove(key);
            return Optional.empty();
        }
        return Optional.of(grant);
    }

    /** Visible for tests: whether the secondary index contains a key. */
    public boolean isGrantIndexed(GrantLookupKey key) {
        return grantIndex.containsKey(key);
    }

    /** Visible for tests. */
    public int grantIndexSize() {
        return grantIndex.size();
    }
}
