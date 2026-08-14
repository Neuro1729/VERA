package com.example.entitlements.validation;

import java.util.List;

public record ConfigurationValidationIssue(
        ValidationSeverity severity,
        String code,
        ValidationDomain domain,
        String entityType,
        String entityId,
        String message,
        List<String> relatedEntityIds
) {
    public ConfigurationValidationIssue {
        relatedEntityIds = relatedEntityIds == null ? List.of() : List.copyOf(relatedEntityIds);
    }

    public static ConfigurationValidationIssue error(
            String code,
            ValidationDomain domain,
            String entityType,
            String entityId,
            String message,
            String... related
    ) {
        return new ConfigurationValidationIssue(
                ValidationSeverity.ERROR,
                code,
                domain,
                entityType,
                entityId,
                message,
                related == null ? List.of() : List.of(related));
    }
}
