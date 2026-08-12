package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionKey;
import com.example.entitlements.domain.*;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class EntitlementResolver {
    private final GrantResolutionCache cache;

    public EntitlementResolver(GrantResolutionCache cache) {
        this.cache = cache;
    }

    public Optional<ResolvedEntitlement> resolve(Tenant tenant, String subjectId, String resourceId, String entitlementKey) {
        Subject subject = tenant.getSubjects().get(subjectId);
        if (subject == null) throw new NoSuchElementException("subject not found: " + subjectId);
        if (!tenant.getResources().containsKey(resourceId)) throw new NoSuchElementException("resource not found: " + resourceId);

        ResolutionKey key = new ResolutionKey(tenant.getId(), subject.getId(), resourceId, entitlementKey);
        Optional<String> cachedGrantId = cache.get(key);
        if (cachedGrantId.isPresent()) {
            EntitlementGrant cachedGrant = tenant.getGrants().get(cachedGrantId.get());
            if (cachedGrant != null) {
                return Optional.of(new ResolvedEntitlement(cachedGrant));
            }
            cache.remove(key);
        }

        Optional<ResolvedEntitlement> resolved = resolveHierarchy(tenant, subject, resourceId, entitlementKey);
        resolved.ifPresent(result -> cache.put(key, result.grant().id()));
        return resolved;
    }

    private Optional<ResolvedEntitlement> resolveHierarchy(
            Tenant tenant,
            Subject subject,
            String resourceId,
            String entitlementKey
    ) {
        EntitlementGrant subjectGrant = ModelValidation.findExactGrant(
                tenant, new Target(TargetType.SUBJECT, subject.getId()), resourceId, entitlementKey);
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
