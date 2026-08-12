package com.example.entitlements.store;

import com.example.entitlements.domain.Tenant;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TenantRegistry {
    private final ConcurrentMap<String, Tenant> tenants = new ConcurrentHashMap<>();

    public void register(Tenant tenant) {
        if (tenants.putIfAbsent(tenant.getId(), tenant) != null) {
            throw new IllegalArgumentException("tenant already exists: " + tenant.getId());
        }
    }

    public Tenant getRequired(String tenantId) {
        Tenant tenant = tenants.get(tenantId);
        if (tenant == null) throw new NoSuchElementException("tenant not found: " + tenantId);
        return tenant;
    }

    public Collection<Tenant> all() {
        return tenants.values();
    }

    public void clear() {
        tenants.clear();
    }
}
