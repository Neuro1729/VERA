package com.example.entitlements.api;

import com.example.entitlements.service.ResourceDistributionResult;
import com.example.entitlements.service.ResourceDistributionService;
import com.example.entitlements.service.ResourceLiveResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class ResourceDistributionController {
    private final ResourceDistributionService distributionService;

    public ResourceDistributionController(ResourceDistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @GetMapping("/{tenantId}/resources/{resourceId}/distribution")
    public ResourceDistributionResult distribution(
            @PathVariable String tenantId,
            @PathVariable String resourceId,
            @RequestParam String scopeId
    ) {
        return distributionService.distribute(tenantId, resourceId, scopeId);
    }

    @GetMapping("/{tenantId}/resources/{resourceId}/live")
    public ResourceLiveResult live(
            @PathVariable String tenantId,
            @PathVariable String resourceId
    ) {
        return distributionService.live(tenantId, resourceId);
    }
}
