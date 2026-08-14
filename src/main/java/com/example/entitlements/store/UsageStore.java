package com.example.entitlements.store;

import com.example.entitlements.domain.Usage;
import com.example.entitlements.persistence.UsageRepository;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UsageStore implements UsageRepository {
    private final ConcurrentMap<String, Usage> usageByGrantId = new ConcurrentHashMap<>();

    public Usage get(String grantId) {
        return usageByGrantId.get(grantId);
    }

    public void put(String grantId, Usage usage) {
        usageByGrantId.put(grantId, usage);
    }

    public void remove(String grantId) {
        usageByGrantId.remove(grantId);
    }

    @Override
    public Usage get(String tenantId, String grantId) {
        return get(grantId);
    }

    @Override
    public Usage lock(String tenantId, String grantId) {
        return get(grantId);
    }

    @Override
    public void save(String tenantId, Usage usage) {
        put(usage.getGrantId(), usage);
    }

    @Override
    public void remove(String tenantId, String grantId) {
        remove(grantId);
    }

    @Override
    public Collection<Usage> findAllByTenant(String tenantId) {
        return all();
    }

    @Override
    public Collection<Usage> all() {
        return usageByGrantId.values();
    }

    @Override
    public void clear() {
        usageByGrantId.clear();
    }
}
