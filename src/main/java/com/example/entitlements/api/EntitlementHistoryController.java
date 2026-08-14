package com.example.entitlements.api;

import com.example.entitlements.service.EntitlementHistoryService;
import com.example.entitlements.service.ResourceEntitlementHistory;
import com.example.entitlements.service.TenantAccessService;
import com.example.entitlements.service.TenantEntitlementHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class EntitlementHistoryController {
    private final EntitlementHistoryService historyService;
    private final TenantAccessService tenantAccessService;

    public EntitlementHistoryController(
            EntitlementHistoryService historyService,
            TenantAccessService tenantAccessService
    ) {
        this.historyService = historyService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/{tenantId}/entitlement-history")
    public TenantEntitlementHistory tenantHistory(@PathVariable String tenantId) {
        tenantAccessService.requireAdminTenant(tenantId);
        return historyService.getTenantHistory(tenantId);
    }

    @GetMapping("/{tenantId}/resources/{resourceId}/entitlement-history")
    public ResourceEntitlementHistory history(
            @PathVariable String tenantId,
            @PathVariable String resourceId
    ) {
        tenantAccessService.requireAdminTenant(tenantId);
        return historyService.getHistory(tenantId, resourceId);
    }
}
