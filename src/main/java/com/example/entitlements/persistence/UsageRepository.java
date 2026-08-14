package com.example.entitlements.persistence;

import com.example.entitlements.domain.Usage;

import java.util.Collection;

public interface UsageRepository {
    Usage get(String tenantId, String grantId);

    Usage lock(String tenantId, String grantId);

    void save(String tenantId, Usage usage);

    void remove(String tenantId, String grantId);

    Collection<Usage> findAllByTenant(String tenantId);

    Collection<Usage> all();

    void clear();
}
