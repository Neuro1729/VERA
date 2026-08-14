package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.GrantInput;
import com.example.entitlements.request.RegistrationRequest;
import com.example.entitlements.request.ScopeInput;
import com.example.entitlements.request.SubjectInput;
import com.example.entitlements.validation.ConfigurationValidationIssue;
import com.example.entitlements.validation.ValidationDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CandidateTenants {
    record BuildResult(Tenant tenant, List<ConfigurationValidationIssue> issues) {}

    private CandidateTenants() {}

    static BuildResult from(RegistrationRequest request) {
        List<ConfigurationValidationIssue> issues = new ArrayList<>();
        if (request == null || request.tenant() == null || request.tenant().id() == null || request.tenant().id().isBlank()) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.ORGANIZATION, "TENANT", null,
                    "tenant.id is required."));
            return new BuildResult(null, issues);
        }
        if (request.structure() == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "ROOT_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", null,
                    "root structure is required."));
            return new BuildResult(null, issues);
        }

        Tenant tenant = new Tenant(request.tenant().id(), request.tenant().name());
        try {
            buildScopeTree(tenant, request.structure(), null, issues);
            tenant.setRootScopeId(request.structure().id());
        } catch (IllegalArgumentException ex) {
            issues.add(ConfigurationValidationIssue.error(
                    "INVALID_ORGANIZATION", ValidationDomain.ORGANIZATION, "SCOPE", request.structure().id(),
                    ex.getMessage()));
        }

        for (Resource resource : safe(request.resources())) {
            try {
                ModelValidation.validateResource(resource);
                if (tenant.getResources().putIfAbsent(resource.id(), resource) != null) {
                    issues.add(ConfigurationValidationIssue.error(
                            "DUPLICATE_RESOURCE_ID", ValidationDomain.RESOURCES, "RESOURCE", resource.id(),
                            "duplicate resource id: " + resource.id()));
                }
            } catch (RuntimeException ex) {
                issues.add(ConfigurationValidationIssue.error(
                        "INVALID_RESOURCE_FIELD", ValidationDomain.RESOURCES, "RESOURCE",
                        resource == null ? null : resource.id(),
                        ex.getMessage()));
            }
        }

        for (GrantInput input : safe(request.grants())) {
            try {
                String id = input.id() == null || input.id().isBlank() ? UUID.randomUUID().toString() : input.id();
                EntitlementGrant grant = new EntitlementGrant(
                        id, input.target(), input.resourceId(), input.entitlementKey(), input.value());
                if (tenant.getGrants().containsKey(grant.id())) {
                    issues.add(ConfigurationValidationIssue.error(
                            "DUPLICATE_GRANT_ID", ValidationDomain.GRANTS, "GRANT", grant.id(),
                            "duplicate grant id: " + grant.id()));
                    continue;
                }
                if (tenant.findGrant(grant.target(), grant.resourceId(), grant.entitlementKey()).isPresent()) {
                    issues.add(ConfigurationValidationIssue.error(
                            "DUPLICATE_LOGICAL_GRANT", ValidationDomain.GRANTS, "GRANT", grant.id(),
                            "duplicate grant for target/resource/entitlement"));
                    continue;
                }
                tenant.putGrant(grant);
            } catch (RuntimeException ex) {
                issues.add(ConfigurationValidationIssue.error(
                        "INVALID_GRANT", ValidationDomain.GRANTS, "GRANT",
                        input == null ? null : input.id(),
                        ex.getMessage()));
            }
        }
        return new BuildResult(tenant, issues);
    }

    private static void buildScopeTree(
            Tenant tenant,
            ScopeInput input,
            String parentScopeId,
            List<ConfigurationValidationIssue> issues
    ) {
        Scope scope = new Scope(input.id(), input.kind(), input.name(), input.metadata(), parentScopeId);
        if (tenant.getScopes().putIfAbsent(scope.getId(), scope) != null) {
            issues.add(ConfigurationValidationIssue.error(
                    "DUPLICATE_SCOPE_ID", ValidationDomain.ORGANIZATION, "SCOPE", scope.getId(),
                    "duplicate scope id: " + scope.getId()));
            return;
        }
        if (parentScopeId != null) {
            tenant.getScopes().get(parentScopeId).addChild(scope.getId());
        }
        for (SubjectInput subjectInput : safe(input.subjects())) {
            if (tenant.getSubjects().containsKey(subjectInput.id())) {
                issues.add(ConfigurationValidationIssue.error(
                        "DUPLICATE_SUBJECT_ID", ValidationDomain.ORGANIZATION, "SUBJECT", subjectInput.id(),
                        "duplicate subject id: " + subjectInput.id()));
                continue;
            }
            Subject subject = new Subject(
                    subjectInput.id(), subjectInput.kind(), subjectInput.name(), subjectInput.metadata(), scope.getId());
            tenant.getSubjects().put(subject.getId(), subject);
            scope.addSubject(subject.getId());
        }
        for (ScopeInput child : safe(input.children())) {
            buildScopeTree(tenant, child, scope.getId(), issues);
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
