package com.example.entitlements.request;

import com.example.entitlements.domain.Resource;

import java.util.List;

public record RegistrationRequest(
        TenantInput tenant,
        ScopeInput structure,
        List<Resource> resources,
        List<GrantInput> grants
) {}
