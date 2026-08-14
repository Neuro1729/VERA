package com.example.entitlements.persistence.memory;

import com.example.entitlements.domain.TenantAdmin;
import com.example.entitlements.persistence.TenantAdminRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryTenantAdminRepository implements TenantAdminRepository {
    private final ConcurrentMap<String, TenantAdmin> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> byTenantId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> byNormalizedEmail = new ConcurrentHashMap<>();

    @Override
    public void insert(TenantAdmin admin) {
        if (byTenantId.putIfAbsent(admin.tenantId(), admin.id()) != null) {
            throw new IllegalStateException("tenant already has an administrator");
        }
        if (byNormalizedEmail.putIfAbsent(admin.normalizedEmail(), admin.id()) != null) {
            byTenantId.remove(admin.tenantId(), admin.id());
            throw new IllegalStateException("email already registered");
        }
        byId.put(admin.id(), admin);
    }

    @Override
    public Optional<TenantAdmin> findByNormalizedEmail(String normalizedEmail) {
        String id = byNormalizedEmail.get(normalizedEmail);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<TenantAdmin> findByTenantId(String tenantId) {
        String id = byTenantId.get(tenantId);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsByTenantId(String tenantId) {
        return byTenantId.containsKey(tenantId);
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        return byNormalizedEmail.containsKey(normalizedEmail);
    }

    @Override
    public void clear() {
        byId.clear();
        byTenantId.clear();
        byNormalizedEmail.clear();
    }
}
