package com.example.entitlements.persistence;

import com.example.entitlements.ResourceEntitlementApplication;
import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.service.CommandService;
import com.example.entitlements.service.EntitlementHistoryService;
import com.example.entitlements.service.EntitlementService;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.service.RegistrationService;
import com.example.entitlements.service.ResourceDistributionService;
import com.example.entitlements.service.ResourceUseService;
import com.example.entitlements.service.UsageHistoryService;
import com.example.entitlements.service.UsageService;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = ResourceEntitlementApplication.class,
        properties = "spring.profiles.active=postgres"
)
@ActiveProfiles("postgres")
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {
    @Autowired protected TenantRegistry registry;
    @Autowired protected TenantRepository tenantRepository;
    @Autowired protected UsageRepository usageRepository;
    @Autowired protected RateLimitStateRepository rateLimitStateRepository;
    @Autowired protected EntitlementHistoryRepository entitlementHistoryRepository;
    @Autowired protected UsageHistoryRepository usageHistoryRepository;
    @Autowired protected RegistrationService registrationService;
    @Autowired protected CommandService commandService;
    @Autowired protected UsageService usageService;
    @Autowired protected RateLimitService rateLimitService;
    @Autowired protected EntitlementService entitlementService;
    @Autowired protected ResourceUseService resourceUseService;
    @Autowired protected UsageHistoryService usageHistoryService;
    @Autowired protected EntitlementHistoryService entitlementHistoryService;
    @Autowired protected ResourceDistributionService distributionService;
    @Autowired protected GrantResolutionCache cache;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MockMvc mockMvc;

    @BeforeEach
    void resetPersistence() {
        cache.clear();
        registry.clear();
        usageRepository.clear();
        rateLimitStateRepository.clear();
        entitlementHistoryRepository.clear();
        usageHistoryRepository.clear();
        tenantRepository.clear();
    }

    protected Tenant registerAcme() {
        return registrationService.register(TestFixtures.registration());
    }

    protected Tenant reload(String tenantId) {
        cache.clear();
        registry.evict(tenantId);
        return registry.getRequired(tenantId);
    }
}
