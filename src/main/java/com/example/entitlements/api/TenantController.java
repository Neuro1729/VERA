package com.example.entitlements.api;

import com.example.entitlements.domain.Tenant;
import com.example.entitlements.domain.Usage;
import com.example.entitlements.request.RegistrationRequest;
import com.example.entitlements.service.RegistrationService;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {
    private final RegistrationService registrationService;
    private final TenantRegistry registry;
    private final UsageStore usageStore;

    public TenantController(RegistrationService registrationService, TenantRegistry registry, UsageStore usageStore) {
        this.registrationService = registrationService;
        this.registry = registry;
        this.usageStore = usageStore;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant register(@RequestBody RegistrationRequest request) {
        return registrationService.register(request);
    }

    @GetMapping("/{tenantId}")
    public Tenant get(@PathVariable String tenantId) {
        return registry.getRequired(tenantId);
    }

    @GetMapping
    public Collection<Tenant> all() {
        return registry.all();
    }

    @GetMapping("/{tenantId}/usage")
    public Collection<Usage> usage(@PathVariable String tenantId) {
        registry.getRequired(tenantId);
        return usageStore.all().stream()
                .filter(usage -> registry.getRequired(tenantId).getGrants().containsKey(usage.getGrantId()))
                .toList();
    }
}
