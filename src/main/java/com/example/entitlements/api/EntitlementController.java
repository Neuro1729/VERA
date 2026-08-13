package com.example.entitlements.api;

import com.example.entitlements.request.*;
import com.example.entitlements.service.EntitlementService;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.service.UsageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entitlements")
public class EntitlementController {
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final RateLimitService rateLimitService;

    public EntitlementController(
            EntitlementService entitlementService,
            UsageService usageService,
            RateLimitService rateLimitService
    ) {
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/evaluate")
    public EvaluationResult evaluate(@RequestBody EvaluationRequest request) {
        return entitlementService.evaluate(request);
    }

    @PostMapping("/consume")
    public ConsumptionResult consume(@RequestBody ConsumptionRequest request) {
        return usageService.consume(request);
    }

    @PostMapping("/rate-limit/consume")
    public RateLimitResult consumeRateLimit(@RequestBody RateLimitRequest request) {
        return rateLimitService.tryConsume(request);
    }
}
