package com.example.entitlements.api;

import com.example.entitlements.service.EntitlementHistoryService;
import com.example.entitlements.service.ResourceEntitlementHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class EntitlementHistoryController {
    private final EntitlementHistoryService historyService;

    public EntitlementHistoryController(EntitlementHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{tenantId}/resources/{resourceId}/entitlement-history")
    public ResourceEntitlementHistory history(
            @PathVariable String tenantId,
            @PathVariable String resourceId
    ) {
        return historyService.getHistory(tenantId, resourceId);
    }
}
