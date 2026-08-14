package com.example.entitlements.api;

import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandResult;
import com.example.entitlements.service.CommandService;
import com.example.entitlements.service.TenantAccessService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/commands")
public class CommandController {
    private final CommandService commandService;
    private final TenantAccessService tenantAccessService;

    public CommandController(CommandService commandService, TenantAccessService tenantAccessService) {
        this.commandService = commandService;
        this.tenantAccessService = tenantAccessService;
    }

    @PostMapping
    public CommandResult execute(@RequestBody CommandRequest request) {
        tenantAccessService.requireAdminTenant(request.tenantId());
        return commandService.execute(request);
    }
}
