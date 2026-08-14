package com.example.entitlements.persistence.config;

import com.example.entitlements.persistence.EntitlementHistoryRepository;
import com.example.entitlements.persistence.RateLimitStateRepository;
import com.example.entitlements.persistence.TenantRepository;
import com.example.entitlements.persistence.UsageHistoryRepository;
import com.example.entitlements.persistence.UsageRepository;
import com.example.entitlements.persistence.memory.InMemoryRateLimitStateRepository;
import com.example.entitlements.persistence.memory.InMemoryTenantRepository;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.UsageHistoryStore;
import com.example.entitlements.store.UsageStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@Configuration
@Profile("memory")
public class MemoryPersistenceConfig {
    @Bean
    TenantRepository tenantRepository() {
        return new InMemoryTenantRepository();
    }

    @Bean
    UsageStore usageStore() {
        return new UsageStore();
    }

    @Bean
    UsageRepository usageRepository(UsageStore usageStore) {
        return usageStore;
    }

    @Bean
    RateLimitStateRepository rateLimitStateRepository() {
        return new InMemoryRateLimitStateRepository();
    }

    @Bean
    EntitlementHistoryStore entitlementHistoryStore() {
        return new EntitlementHistoryStore();
    }

    @Bean
    EntitlementHistoryRepository entitlementHistoryRepository(EntitlementHistoryStore store) {
        return store;
    }

    @Bean
    UsageHistoryStore usageHistoryStore() {
        return new UsageHistoryStore();
    }

    @Bean
    UsageHistoryRepository usageHistoryRepository(UsageHistoryStore store) {
        return store;
    }

    @Bean
    PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {}

            @Override
            protected void doCommit(DefaultTransactionStatus status) {}

            @Override
            protected void doRollback(DefaultTransactionStatus status) {}
        };
    }
}
