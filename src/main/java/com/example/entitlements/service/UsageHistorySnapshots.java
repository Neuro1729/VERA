package com.example.entitlements.service;

import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;

final class UsageHistorySnapshots {
    private UsageHistorySnapshots() {}

    static String subjectName(Tenant tenant, String subjectId) {
        Subject subject = tenant.getSubjects().get(subjectId);
        if (subject == null || subject.getName() == null || subject.getName().isBlank()) return subjectId;
        return subject.getName();
    }

    static String grantTargetName(Tenant tenant, Target target) {
        if (target == null) return null;
        if (target.type() == TargetType.SCOPE) {
            Scope scope = tenant.getScopes().get(target.id());
            if (scope == null || scope.getName() == null || scope.getName().isBlank()) return target.id();
            return scope.getName();
        }
        Subject subject = tenant.getSubjects().get(target.id());
        if (subject == null || subject.getName() == null || subject.getName().isBlank()) return target.id();
        return subject.getName();
    }
}
