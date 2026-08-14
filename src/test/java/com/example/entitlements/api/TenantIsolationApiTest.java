package com.example.entitlements.api;

import com.example.entitlements.ResourceEntitlementApplication;
import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.persistence.TenantAdminRepository;
import com.example.entitlements.persistence.TenantApiCredentialRepository;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageHistoryStore;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.SecurityTestSupport;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ResourceEntitlementApplication.class, properties = "vera.security.enabled=true")
@AutoConfigureMockMvc
class TenantIsolationApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired TenantRegistry registry;
    @Autowired UsageStore usageStore;
    @Autowired EntitlementHistoryStore historyStore;
    @Autowired UsageHistoryStore usageHistoryStore;
    @Autowired RateLimitService rateLimitService;
    @Autowired TenantAdminRepository adminRepository;
    @Autowired TenantApiCredentialRepository credentialRepository;
    @Autowired GrantResolutionCache cache;

    private MockHttpSession acmeSession;

    @BeforeEach
    void setUp() throws Exception {
        registry.clear();
        usageStore.clear();
        historyStore.clear();
        usageHistoryStore.clear();
        rateLimitService.clear();
        adminRepository.clear();
        credentialRepository.clear();
        cache.clear();
        acmeSession = SecurityTestSupport.session(
                SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup()));
        SecurityTestSupport.signup(mockMvc, mapper,
                TestFixtures.signup("globex", "Globex", "admin@globex.com", "a-long-password"));
    }

    @Test
    void acmeAdminCanReadAcmeAndCannotReadGlobex() throws Exception {
        mockMvc.perform(get("/api/tenants/acme").session(acmeSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acme"));
        mockMvc.perform(get("/api/tenants/globex").session(acmeSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants").session(acmeSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("acme"));
    }

    @Test
    void bodyAndPathTenantIdsAreNotTrusted() throws Exception {
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/commands")
                        .session(acmeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SET_ENTITLEMENT","tenantId":"globex","payload":{}}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/tenants/globex/sync")
                        .session(acmeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/tenants/globex/sync/preview")
                        .session(acmeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/entitlements/evaluate")
                        .session(acmeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"globex\",\"subjectId\":\"alice\",\"resourceId\":\"api\",\"entitlementKey\":\"api.enabled\",\"requestedValue\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/globex/resources/api/entitlement-history").session(acmeSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/globex/resources/api/usage-history").session(acmeSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/globex/resources/api/live").session(acmeSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/globex/resources/api/distribution?scopeId=root").session(acmeSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/globex/usage").session(acmeSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/globex/entitlement-history").session(acmeSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/globex/usage-history").session(acmeSession))
                .andExpect(status().isForbidden());
    }
}
