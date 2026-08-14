package com.example.entitlements.persistence;

import com.example.entitlements.domain.RateLimitState;

public interface RateLimitStateRepository {
    RateLimitState get(String tenantId, String grantId);

    RateLimitState lock(String tenantId, String grantId);

    void save(String tenantId, String grantId, RateLimitState state);

    void insertIfAbsent(String tenantId, String grantId, RateLimitState state);

    void remove(String tenantId, String grantId);

    boolean exists(String tenantId, String grantId);

    int count();

    void clear();
}
