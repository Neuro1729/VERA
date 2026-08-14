package com.example.entitlements.security;

public record TenantAdminPrincipal(String adminId, String tenantId, String email) {}
