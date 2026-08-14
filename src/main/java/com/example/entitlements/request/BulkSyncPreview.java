package com.example.entitlements.request;

import com.example.entitlements.validation.ConfigurationValidationIssue;
import com.example.entitlements.validation.ValidationDomain;

import java.util.List;

public record BulkSyncPreview(
        boolean valid,
        List<ValidationDomain> domains,
        Summary summary,
        ImpactSummary impactSummary,
        List<Change> changes,
        List<ConfigurationValidationIssue> issues
) {
    public record Summary(
            int scopesAdded,
            int scopesUpdated,
            int scopesMoved,
            int scopesRemoved,
            int subjectsAdded,
            int subjectsUpdated,
            int subjectsMoved,
            int subjectsRemoved,
            int resourcesAdded,
            int resourcesUpdated,
            int resourcesRemoved,
            int grantsCreated,
            int grantsUpdated,
            int grantsRemoved,
            int grantsAutomaticallyRemoved,
            int invalidGrantCount,
            int warningCount,
            int errorCount
    ) {}

    public record ImpactSummary(
            int grantsAffected,
            int grantsAutomaticallyRemoved
    ) {}

    public record Change(
            String type,
            String entityType,
            String entityId,
            String message
    ) {}
}
