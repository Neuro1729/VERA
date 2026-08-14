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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ResourceEntitlementApplication.class, properties = "vera.security.enabled=true")
@AutoConfigureMockMvc
class GatewaySecurityApiTest {
    private static final String EVALUATE = """
            {"subjectId":"alice","resourceId":"api","entitlementKey":"api.enabled","requestedValue":true}
            """;

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

    private String acmeKey;
    private String globexKey;
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
        MvcResult acme = SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        acmeKey = SecurityTestSupport.json(acme, "apiKey");
        acmeSession = SecurityTestSupport.session(acme);
        globexKey = SecurityTestSupport.json(
                SecurityTestSupport.signup(mockMvc, mapper,
                        TestFixtures.signup("globex", "Globex", "admin@globex.com", "a-long-password")),
                "apiKey");
    }

    @Test
    void missingOrInvalidApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", "not-a-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", "vera_live_unknown.secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isUnauthorized());
        String tampered = acmeKey.substring(0, acmeKey.length() - 4) + "xxxx";
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKeyIsBoundToItsTenantAndDoesNotNeedCsrf() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", acmeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(post("/api/gateway/tenants/globex/evaluate")
                        .header("X-VERA-API-KEY", acmeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiKeyCanCallAllGatewayOperations() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", acmeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(post("/api/gateway/tenants/acme/consume")
                        .header("X-VERA-API-KEY", acmeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(java.util.Map.of(
                                "subjectId", "alice",
                                "resourceId", "api",
                                "entitlementKey", "api.requests",
                                "amount", new BigDecimal("10")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(post("/api/gateway/tenants/acme/rate-limit/consume")
                        .header("X-VERA-API-KEY", acmeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.rateLimit","tokens":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(post("/api/gateway/tenants/acme/use")
                        .header("X-VERA-API-KEY", acmeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.models","requestedValue":"large"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void apiKeyAndAdminSessionAreNotInterchangeable() throws Exception {
        mockMvc.perform(get("/api/tenants/acme")
                        .header("X-VERA-API-KEY", acmeKey))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .session(acmeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", globexKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE))
                .andExpect(status().isForbidden());
    }
}
