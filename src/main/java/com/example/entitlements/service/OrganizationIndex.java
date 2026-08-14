package com.example.entitlements.service;

import com.example.entitlements.request.ScopeInput;
import com.example.entitlements.request.SubjectInput;
import com.example.entitlements.validation.ConfigurationValidationIssue;
import com.example.entitlements.validation.ValidationDomain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OrganizationIndex {
    record DesiredScope(
            String id,
            String kind,
            String name,
            Map<String, Object> metadata,
            String parentId,
            int depth
    ) {}

    record DesiredSubject(
            String id,
            String kind,
            String name,
            Map<String, Object> metadata,
            String scopeId
    ) {}

    final Map<String, DesiredScope> scopes = new LinkedHashMap<>();
    final Map<String, DesiredSubject> subjects = new LinkedHashMap<>();
    final List<ConfigurationValidationIssue> issues = new ArrayList<>();
    String rootId;

    static OrganizationIndex from(ScopeInput root) {
        OrganizationIndex index = new OrganizationIndex();
        if (root == null) {
            index.issues.add(ConfigurationValidationIssue.error(
                    "ROOT_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", null,
                    "Organization structure root is required."));
            return index;
        }
        index.rootId = root.id();
        index.walk(root, null, 0);
        return index;
    }

    private void walk(ScopeInput input, String parentId, int depth) {
        if (input == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", parentId,
                    "Scope child is null.", parentId));
            return;
        }
        if (input.id() == null || input.id().isBlank()) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", null,
                    "Scope id is required."));
            return;
        }
        if (input.kind() == null || input.kind().isBlank()) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", input.id(),
                    "Scope kind is required for " + input.id() + "."));
        }
        if (scopes.containsKey(input.id())) {
            issues.add(ConfigurationValidationIssue.error(
                    "DUPLICATE_SCOPE_ID", ValidationDomain.ORGANIZATION, "SCOPE", input.id(),
                    "Duplicate scope id in submitted organization: " + input.id() + "."));
            return;
        }
        Map<String, Object> metadata = input.metadata() == null ? Map.of() : input.metadata();
        scopes.put(input.id(), new DesiredScope(
                input.id(),
                input.kind(),
                input.name() == null || input.name().isBlank() ? input.id() : input.name(),
                metadata,
                parentId,
                depth));

        for (SubjectInput subject : safe(input.subjects())) {
            addSubject(subject, input.id());
        }
        for (ScopeInput child : safe(input.children())) {
            walk(child, input.id(), depth + 1);
        }
    }

    private void addSubject(SubjectInput input, String scopeId) {
        if (input == null || input.id() == null || input.id().isBlank()) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.ORGANIZATION, "SUBJECT", null,
                    "Subject id is required under scope " + scopeId + ".", scopeId));
            return;
        }
        if (input.kind() == null || input.kind().isBlank()) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.ORGANIZATION, "SUBJECT", input.id(),
                    "Subject kind is required for " + input.id() + "."));
        }
        if (subjects.containsKey(input.id())) {
            issues.add(ConfigurationValidationIssue.error(
                    "DUPLICATE_SUBJECT_ID", ValidationDomain.ORGANIZATION, "SUBJECT", input.id(),
                    "Duplicate subject id in submitted organization: " + input.id() + "."));
            return;
        }
        Map<String, Object> metadata = input.metadata() == null ? Map.of() : input.metadata();
        subjects.put(input.id(), new DesiredSubject(
                input.id(),
                input.kind(),
                input.name() == null || input.name().isBlank() ? input.id() : input.name(),
                metadata,
                scopeId));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
