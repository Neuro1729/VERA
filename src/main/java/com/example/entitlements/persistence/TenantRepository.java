package com.example.entitlements.persistence;

import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.Tenant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenantRepository {
    void insert(Tenant tenant);

    Optional<Tenant> findById(String tenantId);

    boolean existsById(String tenantId);

    List<String> findAllIds();

    void insertScope(String tenantId, Scope scope);

    void updateScope(String tenantId, Scope scope);

    void updateScopeParent(String tenantId, String scopeId, String parentScopeId);

    void deleteScopes(String tenantId, Collection<String> scopeIds);

    void insertSubject(String tenantId, Subject subject);

    void updateSubject(String tenantId, Subject subject);

    void updateSubjectScope(String tenantId, String subjectId, String scopeId);

    void deleteSubjects(String tenantId, Collection<String> subjectIds);

    void insertResource(String tenantId, Resource resource);

    void updateResource(String tenantId, Resource resource);

    void deleteResource(String tenantId, String resourceId);

    void upsertGrant(String tenantId, EntitlementGrant grant);

    void deleteGrant(String tenantId, String grantId);

    void deleteGrants(String tenantId, Collection<String> grantIds);

    void clear();
}
