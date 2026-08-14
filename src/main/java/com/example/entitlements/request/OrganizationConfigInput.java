package com.example.entitlements.request;

public record OrganizationConfigInput(
        TenantInput tenant,
        ScopeInput structure
) {}
