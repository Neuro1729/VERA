package com.example.entitlements.store;

import com.example.entitlements.domain.Tenant;
import com.example.entitlements.persistence.TenantRepository;
import com.example.entitlements.persistence.memory.InMemoryTenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TenantRegistry {
    private final ConcurrentMap<String, Tenant> tenants = new ConcurrentHashMap<>();
    private final TenantRepository tenantRepository;

    public TenantRegistry() {
        this(new InMemoryTenantRepository());
    }

    @Autowired
    public TenantRegistry(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public void register(Tenant tenant) {
        if (tenants.putIfAbsent(tenant.getId(), tenant) != null) {
            throw new IllegalArgumentException("tenant already exists: " + tenant.getId());
        }
    }

    public void put(Tenant tenant) {
        tenants.put(tenant.getId(), tenant);
    }

    public void evict(String tenantId) {
        if (tenantId != null) tenants.remove(tenantId);
    }

    public Tenant getRequired(String tenantId) {
        Tenant cached = tenants.get(tenantId);
        if (cached != null) return cached;
        Tenant loaded = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("tenant not found: " + tenantId));
        Tenant raced = tenants.putIfAbsent(tenantId, loaded);
        return raced != null ? raced : loaded;
    }

    public Collection<Tenant> all() {
        Map<String, Tenant> result = new LinkedHashMap<>();
        tenants.forEach(result::put);
        for (String id : tenantRepository.findAllIds()) {
            result.computeIfAbsent(id, this::getRequired);
        }
        return result.values();
    }

    public void clear() {
        tenants.clear();
        tenantRepository.clear();
    }
}
