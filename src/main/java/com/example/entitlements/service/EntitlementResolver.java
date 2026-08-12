package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class EntitlementResolver {
    public Optional<ResolvedEntitlement> resolve(Tenant tenant, String subjectId, String resourceId, String entitlementKey) {
        Subject subject = tenant.getSubjects().get(subjectId);
        if (subject == null) throw new NoSuchElementException("subject not found: " + subjectId);
        if (!tenant.getResources().containsKey(resourceId)) throw new NoSuchElementException("resource not found: " + resourceId);

        EntitlementGrant subjectGrant = ModelValidation.findExactGrant(
                tenant, new Target(TargetType.SUBJECT, subjectId), resourceId, entitlementKey);
        if (subjectGrant != null) return Optional.of(new ResolvedEntitlement(subjectGrant));

        String scopeId = subject.getScopeId();
        while (scopeId != null) {
            EntitlementGrant scopeGrant = ModelValidation.findExactGrant(
                    tenant, new Target(TargetType.SCOPE, scopeId), resourceId, entitlementKey);
            if (scopeGrant != null) return Optional.of(new ResolvedEntitlement(scopeGrant));

            Scope scope = tenant.getScopes().get(scopeId);
            if (scope == null) break;
            scopeId = scope.getParentScopeId();
        }
        return Optional.empty();
    }
}
