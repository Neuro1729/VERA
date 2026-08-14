package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.Tenant;

final class TenantCopy {
    private TenantCopy() {}

    static Tenant deepCopy(Tenant source) {
        Tenant copy = new Tenant(source.getId(), source.getName());
        copy.setRootScopeId(source.getRootScopeId());
        for (Scope scope : source.getScopes().values()) {
            copy.getScopes().put(scope.getId(), new Scope(
                    scope.getId(),
                    scope.getKind(),
                    scope.getName(),
                    scope.getMetadata(),
                    scope.getParentScopeId()));
        }
        for (Scope scope : source.getScopes().values()) {
            Scope replica = copy.getScopes().get(scope.getId());
            for (String childId : scope.getChildScopeIds()) {
                replica.addChild(childId);
            }
            for (String subjectId : scope.getSubjectIds()) {
                replica.addSubject(subjectId);
            }
        }
        for (Subject subject : source.getSubjects().values()) {
            copy.getSubjects().put(subject.getId(), new Subject(
                    subject.getId(),
                    subject.getKind(),
                    subject.getName(),
                    subject.getMetadata(),
                    subject.getScopeId()));
        }
        for (Resource resource : source.getResources().values()) {
            copy.getResources().put(resource.id(), resource);
        }
        for (EntitlementGrant grant : source.getGrants().values()) {
            copy.putGrant(grant);
        }
        return copy;
    }
}
