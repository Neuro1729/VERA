package com.example.entitlements.persistence;

import com.example.entitlements.domain.TenantApiCredential;

import java.util.Optional;

public interface TenantApiCredentialRepository {
    void insert(TenantApiCredential credential);

    void replace(TenantApiCredential credential);

    Optional<TenantApiCredential> findByPublicId(String publicId);

    Optional<TenantApiCredential> findByTenantId(String tenantId);

    void clear();
}
