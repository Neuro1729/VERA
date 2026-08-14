package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementDefinition;
import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.GrantLookupKey;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.CompanyRegistrationRequest;
import com.example.entitlements.request.GrantInput;
import com.example.entitlements.request.RegistrationRequest;
import com.example.entitlements.validation.ConfigurationValidationIssue;
import com.example.entitlements.validation.ValidationDomain;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConfigurationValidationService {

    public List<ConfigurationValidationIssue> validate(Tenant tenant) {
        List<ConfigurationValidationIssue> issues = new ArrayList<>();
        if (tenant == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "ROOT_MISSING", ValidationDomain.ORGANIZATION, "TENANT", null,
                    "Tenant is required."));
            return issues;
        }
        validateOrganization(tenant, issues);
        validateResources(tenant, issues);
        validateGrants(tenant, issues);
        return issues;
    }

    public RegistrationRequest toRegistrationRequest(CompanyRegistrationRequest request) {
        if (request == null || request.organization() == null) {
            throw new IllegalArgumentException("organization is required");
        }
        if (request.organization().tenant() == null) {
            throw new IllegalArgumentException("organization.tenant is required");
        }
        if (request.organization().structure() == null) {
            throw new IllegalArgumentException("organization.structure is required");
        }
        List<Resource> resources = request.resources() == null || request.resources().resources() == null
                ? List.of() : request.resources().resources();
        List<GrantInput> grants = request.grants() == null || request.grants().grants() == null
                ? List.of() : request.grants().grants();
        return new RegistrationRequest(
                request.organization().tenant(),
                request.organization().structure(),
                resources,
                grants);
    }

    private void validateOrganization(Tenant tenant, List<ConfigurationValidationIssue> issues) {
        String rootId = tenant.getRootScopeId();
        if (rootId == null || rootId.isBlank()) {
            issues.add(ConfigurationValidationIssue.error(
                    "ROOT_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", null,
                    "Root scope is required."));
            return;
        }
        Scope root = tenant.getScopes().get(rootId);
        if (root == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "ROOT_ID_INVALID", ValidationDomain.ORGANIZATION, "SCOPE", rootId,
                    "Root scope " + rootId + " does not exist."));
            return;
        }
        if (root.getParentScopeId() != null) {
            issues.add(ConfigurationValidationIssue.error(
                    "ROOT_ID_INVALID", ValidationDomain.ORGANIZATION, "SCOPE", rootId,
                    "Root scope " + rootId + " must not have a parent."));
        }

        Set<String> claimedSubjects = new HashSet<>();
        for (Scope scope : tenant.getScopes().values()) {
            String parentId = scope.getParentScopeId();
            if (parentId != null) {
                Scope parent = tenant.getScopes().get(parentId);
                if (parent == null) {
                    issues.add(ConfigurationValidationIssue.error(
                            "SCOPE_PARENT_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", scope.getId(),
                            "Scope " + scope.getId() + " references missing parent " + parentId + ".",
                            parentId));
                } else if (!parent.getChildScopeIds().contains(scope.getId())) {
                    issues.add(ConfigurationValidationIssue.error(
                            "SCOPE_PARENT_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", scope.getId(),
                            "Parent " + parentId + " does not list child " + scope.getId() + ".",
                            parentId));
                }
            }
            if (hasCycle(tenant, scope.getId())) {
                issues.add(ConfigurationValidationIssue.error(
                        "SCOPE_CYCLE", ValidationDomain.ORGANIZATION, "SCOPE", scope.getId(),
                        "Scope hierarchy contains a cycle at " + scope.getId() + "."));
            }
            for (String childId : scope.getChildScopeIds()) {
                Scope child = tenant.getScopes().get(childId);
                if (child == null) {
                    issues.add(ConfigurationValidationIssue.error(
                            "SCOPE_PARENT_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", childId,
                            "Scope " + scope.getId() + " lists missing child " + childId + ".",
                            scope.getId()));
                }
            }
            for (String subjectId : scope.getSubjectIds()) {
                if (!claimedSubjects.add(subjectId)) {
                    issues.add(ConfigurationValidationIssue.error(
                            "DUPLICATE_SUBJECT_ID", ValidationDomain.ORGANIZATION, "SUBJECT", subjectId,
                            "Subject " + subjectId + " belongs to more than one scope."));
                }
                Subject subject = tenant.getSubjects().get(subjectId);
                if (subject == null) {
                    issues.add(ConfigurationValidationIssue.error(
                            "SUBJECT_SCOPE_MISSING", ValidationDomain.ORGANIZATION, "SUBJECT", subjectId,
                            "Scope " + scope.getId() + " lists missing subject " + subjectId + ".",
                            scope.getId()));
                } else if (!scope.getId().equals(subject.getScopeId())) {
                    issues.add(ConfigurationValidationIssue.error(
                            "SUBJECT_SCOPE_MISSING", ValidationDomain.ORGANIZATION, "SUBJECT", subjectId,
                            "Subject " + subjectId + " is listed under " + scope.getId()
                                    + " but belongs to " + subject.getScopeId() + ".",
                            subject.getScopeId()));
                }
            }
        }

        for (Subject subject : tenant.getSubjects().values()) {
            Scope scope = tenant.getScopes().get(subject.getScopeId());
            if (scope == null) {
                issues.add(ConfigurationValidationIssue.error(
                        "SUBJECT_SCOPE_MISSING", ValidationDomain.ORGANIZATION, "SUBJECT", subject.getId(),
                        "Subject " + subject.getId() + " belongs to missing scope " + subject.getScopeId() + ".",
                        subject.getScopeId()));
            } else if (!scope.getSubjectIds().contains(subject.getId())) {
                issues.add(ConfigurationValidationIssue.error(
                        "SUBJECT_SCOPE_MISSING", ValidationDomain.ORGANIZATION, "SUBJECT", subject.getId(),
                        "Scope " + subject.getScopeId() + " does not list subject " + subject.getId() + ".",
                        subject.getScopeId()));
            }
        }
    }

    private void validateResources(Tenant tenant, List<ConfigurationValidationIssue> issues) {
        for (Resource resource : tenant.getResources().values()) {
            Set<String> keys = new HashSet<>();
            for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
                if (!keys.add(definition.key())) {
                    issues.add(ConfigurationValidationIssue.error(
                            "DUPLICATE_ENTITLEMENT_DEFINITION", ValidationDomain.RESOURCES, "RESOURCE", resource.id(),
                            "Duplicate entitlement definition on resource " + resource.id() + ": " + definition.key() + ".",
                            definition.key()));
                }
            }
            try {
                ModelValidation.validateResource(resource);
            } catch (IllegalArgumentException ex) {
                if (issues.stream().noneMatch(issue ->
                        issue.entityId() != null && issue.entityId().equals(resource.id())
                                && "DUPLICATE_ENTITLEMENT_DEFINITION".equals(issue.code()))) {
                    issues.add(ConfigurationValidationIssue.error(
                            "INVALID_RESOURCE_FIELD", ValidationDomain.RESOURCES, "RESOURCE", resource.id(),
                            ex.getMessage()));
                }
            }
        }
    }

    private void validateGrants(Tenant tenant, List<ConfigurationValidationIssue> issues) {
        Set<GrantLookupKey> seenKeys = new LinkedHashSet<>();
        for (EntitlementGrant grant : tenant.getGrants().values()) {
            GrantLookupKey key = GrantLookupKey.from(grant);
            if (!seenKeys.add(key)) {
                issues.add(ConfigurationValidationIssue.error(
                        "DUPLICATE_LOGICAL_GRANT", ValidationDomain.GRANTS, "GRANT", grant.id(),
                        "Duplicate logical grant for " + key.targetType() + "/" + key.targetId()
                                + "/" + key.resourceId() + "/" + key.entitlementKey() + "."));
            }
            validateGrant(tenant, grant, issues);
        }
    }

    void validateGrant(Tenant tenant, EntitlementGrant grant, List<ConfigurationValidationIssue> issues) {
        if (grant.target().type() == TargetType.SCOPE) {
            if (!tenant.getScopes().containsKey(grant.target().id())) {
                issues.add(ConfigurationValidationIssue.error(
                        "GRANT_TARGET_MISSING", ValidationDomain.GRANTS, "GRANT", grant.id(),
                        "Grant " + grant.id() + " targets scope " + grant.target().id()
                                + ", but that scope does not exist in the projected organization.",
                        grant.target().id()));
            }
        } else if (grant.target().type() == TargetType.SUBJECT) {
            if (!tenant.getSubjects().containsKey(grant.target().id())) {
                issues.add(ConfigurationValidationIssue.error(
                        "GRANT_TARGET_MISSING", ValidationDomain.GRANTS, "GRANT", grant.id(),
                        "Grant " + grant.id() + " targets subject " + grant.target().id()
                                + ", but that subject does not exist in the projected organization.",
                        grant.target().id()));
            }
        } else {
            issues.add(ConfigurationValidationIssue.error(
                    "GRANT_TARGET_TYPE_INVALID", ValidationDomain.GRANTS, "GRANT", grant.id(),
                    "Grant " + grant.id() + " has an invalid target type."));
        }

        Resource resource = tenant.getResources().get(grant.resourceId());
        if (resource == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "GRANT_RESOURCE_MISSING", ValidationDomain.GRANTS, "GRANT", grant.id(),
                    "Grant " + grant.id() + " references missing resource " + grant.resourceId() + ".",
                    grant.resourceId()));
            return;
        }
        EntitlementDefinition definition = resource.definition(grant.entitlementKey());
        if (definition == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "GRANT_ENTITLEMENT_DEFINITION_MISSING", ValidationDomain.GRANTS, "GRANT", grant.id(),
                    "Grant " + grant.id() + " would become invalid because resource " + grant.resourceId()
                            + " no longer defines entitlement " + grant.entitlementKey() + ".",
                    grant.resourceId(), grant.entitlementKey()));
            return;
        }
        if (definition.valueType() != grant.value().valueType()) {
            issues.add(ConfigurationValidationIssue.error(
                    "GRANT_VALUE_TYPE_MISMATCH", ValidationDomain.GRANTS, "GRANT", grant.id(),
                    "Grant " + grant.id() + " value type " + grant.value().valueType()
                            + " does not match definition " + definition.valueType() + " for "
                            + grant.entitlementKey() + ".",
                    grant.entitlementKey()));
        }
    }

    static boolean hasCycle(Tenant tenant, String startId) {
        Set<String> seen = new HashSet<>();
        String current = startId;
        while (current != null) {
            if (!seen.add(current)) return true;
            Scope scope = tenant.getScopes().get(current);
            if (scope == null) return false;
            current = scope.getParentScopeId();
        }
        return false;
    }

    static int invalidGrantCount(List<ConfigurationValidationIssue> issues) {
        Set<String> ids = new HashSet<>();
        for (ConfigurationValidationIssue issue : issues) {
            if (issue.domain() == ValidationDomain.GRANTS
                    && issue.entityType() != null
                    && issue.entityType().equals("GRANT")
                    && issue.entityId() != null
                    && issue.code() != null
                    && issue.code().startsWith("GRANT_")
                    && !issue.code().equals("DUPLICATE_GRANT_ID")
                    && !issue.code().equals("DUPLICATE_LOGICAL_GRANT")) {
                ids.add(issue.entityId());
            }
        }
        return ids.size();
    }
}
