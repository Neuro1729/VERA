package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.request.*;
import com.example.entitlements.store.TenantRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationService {
    private final TenantRegistry registry;
    private final EntitlementHistoryService historyService;

    public RegistrationService(TenantRegistry registry, EntitlementHistoryService historyService) {
        this.registry = registry;
        this.historyService = historyService;
    }

    public Tenant register(RegistrationRequest request) {
        if (request == null || request.tenant() == null) throw new IllegalArgumentException("tenant is required");
        if (request.structure() == null) throw new IllegalArgumentException("root structure is required");

        Tenant tenant = new Tenant(request.tenant().id(), request.tenant().name());
        buildScopeTree(tenant, request.structure(), null);
        tenant.setRootScopeId(request.structure().id());

        for (Resource resource : safe(request.resources())) {
            ModelValidation.validateResource(resource);
            if (tenant.getResources().putIfAbsent(resource.id(), resource) != null) {
                throw new IllegalArgumentException("duplicate resource id: " + resource.id());
            }
        }

        List<EntitlementGrant> created = new ArrayList<>();
        for (GrantInput input : safe(request.grants())) {
            String id = input.id() == null || input.id().isBlank() ? UUID.randomUUID().toString() : input.id();
            EntitlementGrant grant = new EntitlementGrant(id, input.target(), input.resourceId(), input.entitlementKey(), input.value());
            ModelValidation.validateGrant(tenant, grant);
            if (ModelValidation.findExactGrant(tenant, grant.target(), grant.resourceId(), grant.entitlementKey()) != null) {
                throw new IllegalArgumentException("duplicate grant for target/resource/entitlement");
            }
            if (tenant.getGrants().containsKey(grant.id())) {
                throw new IllegalArgumentException("duplicate grant id: " + grant.id());
            }
            tenant.putGrant(grant);
            created.add(grant);
        }

        registry.register(tenant);
        for (EntitlementGrant grant : created) {
            historyService.recordCreated(tenant.getId(), grant);
        }
        return tenant;
    }

    private void buildScopeTree(Tenant tenant, ScopeInput input, String parentScopeId) {
        Scope scope = new Scope(input.id(), input.kind(), input.name(), input.metadata(), parentScopeId);
        if (tenant.getScopes().putIfAbsent(scope.getId(), scope) != null) {
            throw new IllegalArgumentException("duplicate scope id: " + scope.getId());
        }

        if (parentScopeId != null) tenant.getScopes().get(parentScopeId).addChild(scope.getId());

        for (SubjectInput subjectInput : safe(input.subjects())) {
            if (tenant.getSubjects().containsKey(subjectInput.id())) {
                throw new IllegalArgumentException("duplicate subject id: " + subjectInput.id());
            }
            Subject subject = new Subject(subjectInput.id(), subjectInput.kind(), subjectInput.name(), subjectInput.metadata(), scope.getId());
            tenant.getSubjects().put(subject.getId(), subject);
            scope.addSubject(subject.getId());
        }

        for (ScopeInput child : safe(input.children())) {
            buildScopeTree(tenant, child, scope.getId());
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
