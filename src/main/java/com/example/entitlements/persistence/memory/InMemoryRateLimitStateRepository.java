package com.example.entitlements.persistence.memory;

import com.example.entitlements.domain.RateLimitState;
import com.example.entitlements.persistence.RateLimitStateRepository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryRateLimitStateRepository implements RateLimitStateRepository {
    private final ConcurrentMap<String, RateLimitState> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitState get(String tenantId, String grantId) {
        return buckets.get(key(tenantId, grantId));
    }

    @Override
    public RateLimitState lock(String tenantId, String grantId) {
        return get(tenantId, grantId);
    }

    @Override
    public void save(String tenantId, String grantId, RateLimitState state) {
        buckets.put(key(tenantId, grantId), state);
    }

    @Override
    public void insertIfAbsent(String tenantId, String grantId, RateLimitState state) {
        buckets.putIfAbsent(key(tenantId, grantId), state);
    }

    @Override
    public void remove(String tenantId, String grantId) {
        buckets.remove(key(tenantId, grantId));
    }

    @Override
    public boolean exists(String tenantId, String grantId) {
        return buckets.containsKey(key(tenantId, grantId));
    }

    @Override
    public int count() {
        return buckets.size();
    }

    @Override
    public void clear() {
        buckets.clear();
    }

    private static String key(String tenantId, String grantId) {
        return tenantId + ":" + grantId;
    }
}
