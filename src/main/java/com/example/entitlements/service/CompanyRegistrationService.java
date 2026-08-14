package com.example.entitlements.service;

import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.CompanyRegistrationRequest;
import com.example.entitlements.request.RegistrationPreview;
import com.example.entitlements.request.RegistrationRequest;
import com.example.entitlements.validation.ConfigurationValidationIssue;
import com.example.entitlements.validation.ValidationSeverity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CompanyRegistrationService {
    private final RegistrationService registrationService;
    private final ConfigurationValidationService validationService;

    public CompanyRegistrationService(
            RegistrationService registrationService,
            ConfigurationValidationService validationService
    ) {
        this.registrationService = registrationService;
        this.validationService = validationService;
    }

    public RegistrationPreview preview(CompanyRegistrationRequest request) {
        List<ConfigurationValidationIssue> issues = new ArrayList<>();
        RegistrationRequest assembled;
        try {
            assembled = validationService.toRegistrationRequest(request);
        } catch (IllegalArgumentException ex) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING",
                    com.example.entitlements.validation.ValidationDomain.ORGANIZATION,
                    "TENANT",
                    null,
                    ex.getMessage()));
            return new RegistrationPreview(false, emptySummary(issues), issues);
        }

        CandidateTenants.BuildResult built = CandidateTenants.from(assembled);
        issues.addAll(built.issues());
        Tenant candidate = built.tenant();
        int invalidGrants = 0;
        if (candidate != null) {
            List<ConfigurationValidationIssue> projected = validationService.validate(candidate);
            issues.addAll(projected);
            invalidGrants = ConfigurationValidationService.invalidGrantCount(projected);
        }
        boolean valid = issues.stream().noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
        return new RegistrationPreview(valid, summary(candidate, issues, invalidGrants), List.copyOf(issues));
    }

    @Transactional
    public Tenant register(CompanyRegistrationRequest request) {
        RegistrationPreview preview = preview(request);
        if (!preview.valid()) {
            throw new IllegalArgumentException(firstError(preview.issues()));
        }
        return registrationService.register(validationService.toRegistrationRequest(request));
    }

    public RegistrationRequest toRegistrationRequest(CompanyRegistrationRequest request) {
        return validationService.toRegistrationRequest(request);
    }

    private static RegistrationPreview.Summary summary(
            Tenant tenant,
            List<ConfigurationValidationIssue> issues,
            int invalidGrants
    ) {
        int errorCount = (int) issues.stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR).count();
        int warningCount = (int) issues.stream().filter(issue -> issue.severity() == ValidationSeverity.WARNING).count();
        if (tenant == null) {
            return new RegistrationPreview.Summary(0, 0, 0, 0, 0, invalidGrants, errorCount, warningCount);
        }
        int definitions = tenant.getResources().values().stream()
                .mapToInt(resource -> resource.entitlementDefinitions().size())
                .sum();
        return new RegistrationPreview.Summary(
                tenant.getScopes().size(),
                tenant.getSubjects().size(),
                tenant.getResources().size(),
                definitions,
                tenant.getGrants().size(),
                invalidGrants,
                errorCount,
                warningCount);
    }

    private static RegistrationPreview.Summary emptySummary(List<ConfigurationValidationIssue> issues) {
        int errorCount = (int) issues.stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR).count();
        return new RegistrationPreview.Summary(0, 0, 0, 0, 0, 0, errorCount, 0);
    }

    private static String firstError(List<ConfigurationValidationIssue> issues) {
        return issues.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
                .map(ConfigurationValidationIssue::message)
                .findFirst()
                .orElse("registration is invalid");
    }
}
