package com.example.entitlements.service;

import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.BulkSyncPreview;
import com.example.entitlements.request.BulkSyncRequest;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.GrantsSyncInput;
import com.example.entitlements.request.OrganizationSyncInput;
import com.example.entitlements.request.ResourcesSyncInput;
import com.example.entitlements.store.CacheEvictOnRollback;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.validation.ConfigurationValidationIssue;
import com.example.entitlements.validation.ValidationSeverity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class BulkSyncService {
    private final TenantRegistry registry;
    private final BulkSyncPlanner planner;
    private final ConfigurationValidationService validationService;
    private final CommandService commandService;
    private final ResolutionCacheInvalidator invalidator;

    public BulkSyncService(
            TenantRegistry registry,
            BulkSyncPlanner planner,
            ConfigurationValidationService validationService,
            CommandService commandService,
            ResolutionCacheInvalidator invalidator
    ) {
        this.registry = registry;
        this.planner = planner;
        this.validationService = validationService;
        this.commandService = commandService;
        this.invalidator = invalidator;
    }

    public BulkSyncPreview preview(String tenantId, BulkSyncRequest request) {
        Tenant current = registry.getRequired(tenantId);
        Tenant snapshot = TenantCopy.deepCopy(current);
        BulkSyncPlanner.PlannedSync planned = planner.plan(snapshot, request);
        return toPreview(planned);
    }

    @Transactional
    public BulkSyncPreview apply(String tenantId, BulkSyncRequest request) {
        Tenant current = registry.getRequired(tenantId);
        CacheEvictOnRollback.register(registry, tenantId);
        synchronized (current) {
            BulkSyncPlanner.PlannedSync planned = planner.plan(current, request);
            BulkSyncPreview preview = toPreview(planned);
            if (!preview.valid()) {
                return preview;
            }
            for (CommandRequest command : planned.plan().commands()) {
                commandService.execute(command);
            }
            invalidator.invalidateTenant(tenantId);
            return preview;
        }
    }

    public BulkSyncPreview previewOrganization(String tenantId, OrganizationSyncInput input) {
        return preview(tenantId, new BulkSyncRequest(input, null, null));
    }

    public BulkSyncPreview applyOrganization(String tenantId, OrganizationSyncInput input) {
        return apply(tenantId, new BulkSyncRequest(input, null, null));
    }

    public BulkSyncPreview previewResources(String tenantId, ResourcesSyncInput input) {
        return preview(tenantId, new BulkSyncRequest(null, input, null));
    }

    public BulkSyncPreview applyResources(String tenantId, ResourcesSyncInput input) {
        return apply(tenantId, new BulkSyncRequest(null, input, null));
    }

    public BulkSyncPreview previewGrants(String tenantId, GrantsSyncInput input) {
        return preview(tenantId, new BulkSyncRequest(null, null, input));
    }

    public BulkSyncPreview applyGrants(String tenantId, GrantsSyncInput input) {
        return apply(tenantId, new BulkSyncRequest(null, null, input));
    }

    private BulkSyncPreview toPreview(BulkSyncPlanner.PlannedSync planned) {
        List<ConfigurationValidationIssue> issues = new ArrayList<>(planned.intakeIssues());
        issues.addAll(validationService.validate(planned.projected()));
        int invalidGrants = ConfigurationValidationService.invalidGrantCount(issues);
        int errorCount = (int) issues.stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR).count();
        int warningCount = (int) issues.stream().filter(issue -> issue.severity() == ValidationSeverity.WARNING).count();
        boolean valid = errorCount == 0;
        BulkSyncPlanner.Counts counts = planned.counts();
        BulkSyncPreview.Summary summary = new BulkSyncPreview.Summary(
                counts.scopesAdded(), counts.scopesUpdated(), counts.scopesMoved(), counts.scopesRemoved(),
                counts.subjectsAdded(), counts.subjectsUpdated(), counts.subjectsMoved(), counts.subjectsRemoved(),
                counts.resourcesAdded(), counts.resourcesUpdated(), counts.resourcesRemoved(),
                counts.grantsCreated(), counts.grantsUpdated(), counts.grantsRemoved(),
                counts.grantsAutomaticallyRemoved(),
                invalidGrants, warningCount, errorCount);
        BulkSyncPreview.ImpactSummary impact = new BulkSyncPreview.ImpactSummary(
                counts.grantsAutomaticallyRemoved() + invalidGrants,
                counts.grantsAutomaticallyRemoved());
        return new BulkSyncPreview(valid, planned.domains(), summary, impact, planned.changes(), List.copyOf(issues));
    }
}
