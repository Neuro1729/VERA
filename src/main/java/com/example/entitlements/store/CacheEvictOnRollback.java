package com.example.entitlements.store;

import com.example.entitlements.domain.Tenant;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class CacheEvictOnRollback {
    private CacheEvictOnRollback() {}

    public static void register(TenantRegistry registry, String tenantId) {
        if (registry == null || tenantId == null || tenantId.isBlank()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    registry.evict(tenantId);
                }
            }
        });
    }
}
