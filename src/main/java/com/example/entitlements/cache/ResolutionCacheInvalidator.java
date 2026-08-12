package com.example.entitlements.cache;

import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Tenant;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

@Component
public class ResolutionCacheInvalidator {
    private final GrantResolutionCache cache;

    public ResolutionCacheInvalidator(GrantResolutionCache cache) {
        this.cache = cache;
    }

    public void invalidateScopeSubtree(Tenant tenant, String scopeId) {
        Objects.requireNonNull(tenant, "tenant is required");
        for (String subjectId : collectSubjectIdsInSubtree(tenant, scopeId)) {
            cache.invalidateSubject(tenant.getId(), subjectId);
        }
    }

    public void invalidateScopeEntitlement(
            Tenant tenant,
            String scopeId,
            String resourceId,
            String entitlementKey
    ) {
        Objects.requireNonNull(tenant, "tenant is required");
        for (String subjectId : collectSubjectIdsInSubtree(tenant, scopeId)) {
            cache.invalidateSubjectEntitlement(
                    tenant.getId(), subjectId, resourceId, entitlementKey);
        }
    }

    private Set<String> collectSubjectIdsInSubtree(Tenant tenant, String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId is required");
        }
        Set<String> subjects = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(scopeId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            if (!visited.add(currentId)) {
                continue;
            }
            Scope scope = tenant.getScopes().get(currentId);
            if (scope == null) {
                continue;
            }
            subjects.addAll(scope.getSubjectIds());
            queue.addAll(scope.getChildScopeIds());
        }
        return subjects;
    }
}
