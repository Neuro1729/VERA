package com.example.entitlements.service;

import com.example.entitlements.domain.*;

import java.util.HashSet;
import java.util.Set;

final class ModelValidation {
    private ModelValidation() {}

    static void validateResource(Resource resource) {
        Set<String> keys = new HashSet<>();
        for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
            if (!keys.add(definition.key())) {
                throw new IllegalArgumentException("duplicate entitlement definition on resource " + resource.id() + ": " + definition.key());
            }
        }
    }

    static void validateGrant(Tenant tenant, EntitlementGrant grant) {
        if (grant.target().type() == TargetType.SCOPE && !tenant.getScopes().containsKey(grant.target().id())) {
            throw new IllegalArgumentException("grant target scope not found: " + grant.target().id());
        }
        if (grant.target().type() == TargetType.SUBJECT && !tenant.getSubjects().containsKey(grant.target().id())) {
            throw new IllegalArgumentException("grant target subject not found: " + grant.target().id());
        }

        Resource resource = tenant.getResources().get(grant.resourceId());
        if (resource == null) throw new IllegalArgumentException("grant resource not found: " + grant.resourceId());

        EntitlementDefinition definition = resource.definition(grant.entitlementKey());
        if (definition == null) {
            throw new IllegalArgumentException("entitlement definition not found on resource " + grant.resourceId() + ": " + grant.entitlementKey());
        }
        if (definition.valueType() != grant.value().valueType()) {
            throw new IllegalArgumentException("entitlement type mismatch for " + grant.entitlementKey()
                    + ": expected " + definition.valueType() + " but got " + grant.value().valueType());
        }
    }

    static EntitlementGrant findExactGrant(Tenant tenant, Target target, String resourceId, String entitlementKey) {
        return tenant.getGrants().values().stream()
                .filter(grant -> grant.target().equals(target))
                .filter(grant -> grant.resourceId().equals(resourceId))
                .filter(grant -> grant.entitlementKey().equals(entitlementKey))
                .findFirst()
                .orElse(null);
    }
}
