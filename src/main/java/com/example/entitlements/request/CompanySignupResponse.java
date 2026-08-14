package com.example.entitlements.request;

public record CompanySignupResponse(
        String tenantId,
        AdminView admin,
        String apiKey
) {
    public record AdminView(String email) {}
}
