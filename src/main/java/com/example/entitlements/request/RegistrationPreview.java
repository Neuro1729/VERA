package com.example.entitlements.request;

import com.example.entitlements.validation.ConfigurationValidationIssue;

import java.util.List;

public record RegistrationPreview(
        boolean valid,
        Summary summary,
        List<ConfigurationValidationIssue> issues
) {
    public record Summary(
            int scopeCount,
            int subjectCount,
            int resourceCount,
            int entitlementDefinitionCount,
            int grantCount,
            int invalidGrantCount,
            int errorCount,
            int warningCount
    ) {}
}
