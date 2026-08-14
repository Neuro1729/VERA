package com.example.entitlements.request;

public record CompanyRegistrationRequest(
        OrganizationConfigInput organization,
        ResourcesConfigInput resources,
        GrantsConfigInput grants
) {}
