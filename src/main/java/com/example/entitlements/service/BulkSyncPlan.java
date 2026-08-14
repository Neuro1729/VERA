package com.example.entitlements.service;

import com.example.entitlements.request.CommandRequest;

import java.util.List;

public record BulkSyncPlan(List<CommandRequest> commands) {
    public BulkSyncPlan {
        commands = commands == null ? List.of() : List.copyOf(commands);
    }
}
