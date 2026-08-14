package com.example.entitlements.persistence;

import com.example.entitlements.domain.EntitlementHistoryEvent;

import java.util.List;

public interface EntitlementHistoryRepository {
    void append(EntitlementHistoryEvent event);

    List<EntitlementHistoryEvent> findByResource(String tenantId, String resourceId);

    void clear();
}
