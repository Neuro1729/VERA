package com.example.entitlements.persistence.memory;

import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.persistence.TenantRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores the same Tenant aggregate instances used by {@code TenantRegistry}.
 * Granular mutation methods are no-ops because callers mutate the cached Tenant in place.
 */
public class InMemoryTenantRepository implements TenantRepository {
    private final ConcurrentMap<String, Tenant> tenants = new ConcurrentHashMap<>();

    @Override
    public void insert(Tenant tenant) {
        if (tenants.putIfAbsent(tenant.getId(), tenant) != null) {
            throw new IllegalArgumentException("tenant already exists: " + tenant.getId());
        }
    }

    @Override
    public Optional<Tenant> findById(String tenantId) {
        return Optional.ofNullable(tenants.get(tenantId));
    }

    @Override
    public boolean existsById(String tenantId) {
        return tenants.containsKey(tenantId);
    }

    @Override
    public List<String> findAllIds() {
        return new ArrayList<>(tenants.keySet());
    }

    @Override
    public void insertScope(String tenantId, Scope scope) {}

    @Override
    public void updateScope(String tenantId, Scope scope) {}

    @Override
    public void updateScopeParent(String tenantId, String scopeId, String parentScopeId) {}

    @Override
    public void deleteScopes(String tenantId, Collection<String> scopeIds) {}

    @Override
    public void insertSubject(String tenantId, Subject subject) {}

    @Override
    public void updateSubject(String tenantId, Subject subject) {}

    @Override
    public void updateSubjectScope(String tenantId, String subjectId, String scopeId) {}

    @Override
    public void deleteSubjects(String tenantId, Collection<String> subjectIds) {}

    @Override
    public void insertResource(String tenantId, Resource resource) {}

    @Override
    public void updateResource(String tenantId, Resource resource) {}

    @Override
    public void deleteResource(String tenantId, String resourceId) {}

    @Override
    public void upsertGrant(String tenantId, EntitlementGrant grant) {}

    @Override
    public void deleteGrant(String tenantId, String grantId) {}

    @Override
    public void deleteGrants(String tenantId, Collection<String> grantIds) {}

    @Override
    public void clear() {
        tenants.clear();
    }
}
