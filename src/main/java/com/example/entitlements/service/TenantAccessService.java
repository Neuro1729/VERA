package com.example.entitlements.service;

import com.example.entitlements.domain.Tenant;
import com.example.entitlements.security.ApiKeyPrincipal;
import com.example.entitlements.security.TenantAdminPrincipal;
import com.example.entitlements.store.TenantRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class TenantAccessService {
    private final boolean securityEnabled;

    public TenantAccessService(@Value("${vera.security.enabled:true}") boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public boolean securityEnabled() {
        return securityEnabled;
    }

    public void requireAdminTenant(String requestedTenantId) {
        if (!securityEnabled) return;
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        TenantAdminPrincipal admin = currentAdmin();
        if (!admin.tenantId().equals(requestedTenantId)) {
            throw new AccessDeniedException("tenant access denied");
        }
    }

    public void requireGatewayTenant(String requestedTenantId) {
        if (!securityEnabled) return;
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            throw new InsufficientAuthenticationException("authentication required");
        }
        if (!principal.tenantId().equals(requestedTenantId)) {
            throw new AccessDeniedException("tenant access denied");
        }
    }

    public TenantAdminPrincipal currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantAdminPrincipal principal)) {
            throw new InsufficientAuthenticationException("authentication required");
        }
        return principal;
    }

    public Collection<Tenant> visibleTenants(TenantRegistry registry) {
        if (!securityEnabled) {
            return registry.all();
        }
        return List.of(registry.getRequired(currentAdmin().tenantId()));
    }
}
