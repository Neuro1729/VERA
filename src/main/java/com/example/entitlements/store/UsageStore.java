package com.example.entitlements.store;

import com.example.entitlements.domain.Usage;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class UsageStore {
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

    public Collection<Usage> all() {
        return usageByGrantId.values();
    }

    public void clear() {
        usageByGrantId.clear();
    }
}
