package com.example.entitlements.persistence.config;

import com.example.entitlements.persistence.EntitlementHistoryRepository;
import com.example.entitlements.persistence.JsonbConverter;
import com.example.entitlements.persistence.RateLimitStateRepository;
import com.example.entitlements.persistence.TenantAdminRepository;
import com.example.entitlements.persistence.TenantApiCredentialRepository;
import com.example.entitlements.persistence.TenantRepository;
import com.example.entitlements.persistence.UsageHistoryRepository;
import com.example.entitlements.persistence.UsageRepository;
import com.example.entitlements.persistence.postgres.PostgresEntitlementHistoryRepository;
import com.example.entitlements.persistence.postgres.PostgresRateLimitStateRepository;
import com.example.entitlements.persistence.postgres.PostgresTenantAdminRepository;
import com.example.entitlements.persistence.postgres.PostgresTenantApiCredentialRepository;
import com.example.entitlements.persistence.postgres.PostgresTenantRepository;
import com.example.entitlements.persistence.postgres.PostgresUsageHistoryRepository;
import com.example.entitlements.persistence.postgres.PostgresUsageRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@Profile("!memory")
public class PostgresPersistenceConfig {
    @Bean
    TenantRepository tenantRepository(NamedParameterJdbcTemplate jdbc, JsonbConverter jsonb) {
        return new PostgresTenantRepository(jdbc, jsonb);
    }

    @Bean
    TenantAdminRepository tenantAdminRepository(NamedParameterJdbcTemplate jdbc) {
        return new PostgresTenantAdminRepository(jdbc);
    }

    @Bean
    TenantApiCredentialRepository tenantApiCredentialRepository(NamedParameterJdbcTemplate jdbc) {
        return new PostgresTenantApiCredentialRepository(jdbc);
    }

    @Bean
    UsageRepository usageRepository(NamedParameterJdbcTemplate jdbc) {
        return new PostgresUsageRepository(jdbc);
    }

    @Bean
    RateLimitStateRepository rateLimitStateRepository(NamedParameterJdbcTemplate jdbc) {
        return new PostgresRateLimitStateRepository(jdbc);
    }

    @Bean
    EntitlementHistoryRepository entitlementHistoryRepository(NamedParameterJdbcTemplate jdbc, JsonbConverter jsonb) {
        return new PostgresEntitlementHistoryRepository(jdbc, jsonb);
    }

    @Bean
    UsageHistoryRepository usageHistoryRepository(NamedParameterJdbcTemplate jdbc, JsonbConverter jsonb) {
        return new PostgresUsageHistoryRepository(jdbc, jsonb);
    }
}
