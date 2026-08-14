package com.example.entitlements.request;

public record OrganizationSyncInput(
        SyncMode mode,
        ScopeInput structure
) {}
