package com.example.entitlements.api;

import com.example.entitlements.request.*;
import com.example.entitlements.service.EntitlementService;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.service.ResourceUseService;
import com.example.entitlements.service.TenantAccessService;
import com.example.entitlements.service.UsageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entitlements")
public class EntitlementController {
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final RateLimitService rateLimitService;
    private final ResourceUseService resourceUseService;
    private final TenantAccessService tenantAccessService;

    public EntitlementController(
            EntitlementService entitlementService,
            UsageService usageService,
            RateLimitService rateLimitService,
            ResourceUseService resourceUseService,
            TenantAccessService tenantAccessService
    ) {
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.rateLimitService = rateLimitService;
        this.resourceUseService = resourceUseService;
        this.tenantAccessService = tenantAccessService;
    }

    @PostMapping("/evaluate")
    public EvaluationResult evaluate(@RequestBody EvaluationRequest request) {
        tenantAccessService.requireAdminTenant(request.tenantId());
        return entitlementService.evaluate(request);
    }

    @PostMapping("/use")
    public EvaluationResult use(@RequestBody EvaluationRequest request) {
        tenantAccessService.requireAdminTenant(request.tenantId());
        return resourceUseService.commitUse(request);
    }

    @PostMapping("/consume")
    public ConsumptionResult consume(@RequestBody ConsumptionRequest request) {
        tenantAccessService.requireAdminTenant(request.tenantId());
        return usageService.consume(request);
    }

    @PostMapping("/rate-limit/consume")
    public RateLimitResult consumeRateLimit(@RequestBody RateLimitRequest request) {
        tenantAccessService.requireAdminTenant(request.tenantId());
        return rateLimitService.tryConsume(request);
    }
}
