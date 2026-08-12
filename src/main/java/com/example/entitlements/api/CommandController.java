package com.example.entitlements.api;

import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandResult;
import com.example.entitlements.service.CommandService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/commands")
public class CommandController {
    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public CommandResult execute(@RequestBody CommandRequest request) {
        return commandService.execute(request);
    }
}
