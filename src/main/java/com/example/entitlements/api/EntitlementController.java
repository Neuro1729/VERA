package com.example.entitlements.api;

import com.example.entitlements.request.*;
import com.example.entitlements.service.EntitlementService;
import com.example.entitlements.service.UsageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entitlements")
public class EntitlementController {
    private final EntitlementService entitlementService;
    private final UsageService usageService;

    public EntitlementController(EntitlementService entitlementService, UsageService usageService) {
        this.entitlementService = entitlementService;
        this.usageService = usageService;
    }

    @PostMapping("/evaluate")
    public EvaluationResult evaluate(@RequestBody EvaluationRequest request) {
        return entitlementService.evaluate(request);
    }

    @PostMapping("/consume")
    public ConsumptionResult consume(@RequestBody ConsumptionRequest request) {
        return usageService.consume(request);
    }
}
