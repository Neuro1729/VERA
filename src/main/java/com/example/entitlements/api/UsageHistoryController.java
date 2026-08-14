package com.example.entitlements.api;

import com.example.entitlements.service.UsageHistoryService;
import com.example.entitlements.service.ResourceUsageHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/tenants")
public class UsageHistoryController {
    private final UsageHistoryService historyService;

    public UsageHistoryController(UsageHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{tenantId}/resources/{resourceId}/usage-history")
    public ResourceUsageHistory history(
            @PathVariable String tenantId,
            @PathVariable String resourceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until
    ) {
        return historyService.getHistory(tenantId, resourceId, from, until);
    }
}
