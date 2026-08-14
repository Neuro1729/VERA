package com.example.entitlements.persistence.memory;

import com.example.entitlements.domain.TenantApiCredential;
import com.example.entitlements.persistence.TenantApiCredentialRepository;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryTenantApiCredentialRepository implements TenantApiCredentialRepository {
    private final ConcurrentMap<String, TenantApiCredential> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> byTenantId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> byPublicId = new ConcurrentHashMap<>();

    @Override
    public void insert(TenantApiCredential credential) {
        if (byTenantId.putIfAbsent(credential.tenantId(), credential.id()) != null) {
            throw new IllegalStateException("tenant already has an API credential");
        }
        if (byPublicId.putIfAbsent(credential.publicId(), credential.id()) != null) {
            byTenantId.remove(credential.tenantId(), credential.id());
            throw new IllegalArgumentException("duplicate API credential");
        }
        byId.put(credential.id(), credential);
    }

    @Override
    public void replace(TenantApiCredential credential) {
        TenantApiCredential existing = byId.get(credential.id());
        if (existing == null) {
            throw new NoSuchElementException("API credential not found");
        }
        byPublicId.remove(existing.publicId(), existing.id());
        if (byPublicId.putIfAbsent(credential.publicId(), credential.id()) != null) {
            byPublicId.put(existing.publicId(), existing.id());
            throw new IllegalArgumentException("duplicate API credential");
        }
        byId.put(credential.id(), credential);
        byTenantId.put(credential.tenantId(), credential.id());
    }

    @Override
    public Optional<TenantApiCredential> findByPublicId(String publicId) {
        String id = byPublicId.get(publicId);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<TenantApiCredential> findByTenantId(String tenantId) {
        String id = byTenantId.get(tenantId);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public void clear() {
        byId.clear();
        byTenantId.clear();
        byPublicId.clear();
    }
}
