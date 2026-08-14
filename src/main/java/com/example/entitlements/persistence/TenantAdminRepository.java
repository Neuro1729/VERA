package com.example.entitlements.persistence;

import com.example.entitlements.domain.TenantAdmin;

import java.util.Optional;

public interface TenantAdminRepository {
    void insert(TenantAdmin admin);

    Optional<TenantAdmin> findByNormalizedEmail(String normalizedEmail);

    Optional<TenantAdmin> findByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);

    boolean existsByNormalizedEmail(String normalizedEmail);

    void clear();
}
