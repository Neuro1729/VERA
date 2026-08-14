package com.example.entitlements.api;

import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.ConsumptionResult;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.EvaluationResult;
import com.example.entitlements.request.GatewayConsumptionRequest;
import com.example.entitlements.request.GatewayEvaluationRequest;
import com.example.entitlements.request.GatewayRateLimitRequest;
import com.example.entitlements.request.GatewayUseRequest;
import com.example.entitlements.request.RateLimitRequest;
import com.example.entitlements.request.RateLimitResult;
import com.example.entitlements.service.EntitlementService;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.service.ResourceUseService;
import com.example.entitlements.service.TenantAccessService;
import com.example.entitlements.service.UsageService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gateway/tenants/{tenantId}")
public class CompanyGatewayController {
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final RateLimitService rateLimitService;
    private final ResourceUseService resourceUseService;
    private final TenantAccessService tenantAccessService;

    public CompanyGatewayController(
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
    public EvaluationResult evaluate(
            @PathVariable String tenantId,
            @RequestBody GatewayEvaluationRequest request
    ) {
        tenantAccessService.requireGatewayTenant(tenantId);
        return entitlementService.evaluate(new EvaluationRequest(
                tenantId, request.subjectId(), request.resourceId(), request.entitlementKey(), request.requestedValue()));
    }

    @PostMapping("/consume")
    public ConsumptionResult consume(
            @PathVariable String tenantId,
            @RequestBody GatewayConsumptionRequest request
    ) {
        tenantAccessService.requireGatewayTenant(tenantId);
        return usageService.consume(new ConsumptionRequest(
                tenantId, request.subjectId(), request.resourceId(), request.entitlementKey(), request.amount()));
    }

    @PostMapping("/rate-limit/consume")
    public RateLimitResult consumeRateLimit(
            @PathVariable String tenantId,
            @RequestBody GatewayRateLimitRequest request
    ) {
        tenantAccessService.requireGatewayTenant(tenantId);
        return rateLimitService.tryConsume(new RateLimitRequest(
                tenantId, request.subjectId(), request.resourceId(), request.entitlementKey(), request.tokens()));
    }

    @PostMapping("/use")
    public EvaluationResult use(
            @PathVariable String tenantId,
            @RequestBody GatewayUseRequest request
    ) {
        tenantAccessService.requireGatewayTenant(tenantId);
        return resourceUseService.commitUse(new EvaluationRequest(
                tenantId, request.subjectId(), request.resourceId(), request.entitlementKey(), request.requestedValue()));
    }
}
