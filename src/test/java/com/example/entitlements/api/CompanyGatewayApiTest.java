package com.example.entitlements.api;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.ResourceEntitlementApplication;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageHistoryStore;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ResourceEntitlementApplication.class)
@AutoConfigureMockMvc
class CompanyGatewayApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired TenantRegistry registry;
    @Autowired UsageStore usageStore;
    @Autowired EntitlementHistoryStore historyStore;
    @Autowired UsageHistoryStore usageHistoryStore;
    @Autowired RateLimitService rateLimitService;
    @Autowired GrantResolutionCache cache;

    @BeforeEach
    void setUp() throws Exception {
        registry.clear();
        usageStore.clear();
        historyStore.clear();
        usageHistoryStore.clear();
        rateLimitService.clear();
        cache.clear();
        mockMvc.perform(post("/api/tenants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.registration())))
                .andExpect(status().isCreated());
    }

    @Test
    void oldRegisterEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/api/tenants/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acme"));
    }

    @Test
    void companyRegistrationPreviewAndApply() throws Exception {
        registry.clear();
        usageStore.clear();
        historyStore.clear();
        usageHistoryStore.clear();
        mockMvc.perform(post("/api/company-registration/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.companyRegistration())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.summary.grantCount").value(10));
        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(post("/api/company-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.companyRegistration())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("acme"));
    }

    @Test
    void gatewayEvaluateAllowed() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.enabled","requestedValue":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.grantId").value("g-root-enabled"));
    }

    @Test
    void gatewayEvaluateDenied() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.maxBatch","requestedValue":101}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void evaluateWritesNoUsage() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.requests","requestedValue":500}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(get("/api/tenants/acme/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/tenants/acme/resources/api/usage-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitlements").isEmpty());
    }

    @Test
    void quotaGatewayConsumes() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.requests","amount":500}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.consumed").value(500))
                .andExpect(jsonPath("$.grantId").value("g-eng-quota"));
    }

    @Test
    void quotaGatewayDenialDoesNotConsume() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.requests","amount":1000001}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
        mockMvc.perform(get("/api/tenants/acme/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].consumed").value(0));
        mockMvc.perform(get("/api/tenants/acme/resources/api/usage-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitlements").isEmpty());
    }

    @Test
    void quotaGatewayUpdatesBucket() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.requests","amount":40}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tenants/acme/resources/api/usage-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitlements[0].grants[0].usage[0].type").value("BUCKET"))
                .andExpect(jsonPath("$.entitlements[0].grants[0].usage[0].totalConsumed").value(40));
    }

    @Test
    void rateLimitGatewayConsumes() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/rate-limit/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.rateLimit","tokens":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.grantId").value("g-eng-rate"))
                .andExpect(jsonPath("$.availableTokens").value(99));
    }

    @Test
    void rateLimitDenialUnchanged() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/rate-limit/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.rateLimit","tokens":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(post("/api/gateway/tenants/acme/rate-limit/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.rateLimit","tokens":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.availableTokens").value(0));
    }

    @Test
    void gatewayUseWritesUsageEvent() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.models","requestedValue":["H100"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
        mockMvc.perform(post("/api/gateway/tenants/acme/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.models","requestedValue":["large"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(get("/api/tenants/acme/resources/api/usage-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='api.models')].grants[0].usage[0].type").value("EVENT"));
    }

    @Test
    void gatewayUseRejectsQuota() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.requests","requestedValue":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gatewayUseRejectsRateLimit() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.rateLimit","requestedValue":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTenantReturns404() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/missing/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"alice","resourceId":"api","entitlementKey":"api.enabled","requestedValue":true}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownSubjectMatchesExistingService() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"nobody","resourceId":"api","entitlementKey":"api.enabled","requestedValue":true}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("subject not found: nobody"));
        mockMvc.perform(post("/api/entitlements/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new EvaluationRequest("acme", "nobody", "api", "api.enabled", mapper.valueToTree(true)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("subject not found: nobody"));
    }

    @Test
    void tenantIdComesFromPathNotBody() throws Exception {
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"other","subjectId":"alice","resourceId":"api","entitlementKey":"api.enabled","requestedValue":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void originalEntitlementEndpointsStillWork() throws Exception {
        mockMvc.perform(post("/api/entitlements/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(500)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(post("/api/entitlements/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new ConsumptionRequest("acme", "alice", "api", "api.requests", BigDecimal.TEN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void bulkSyncHttpEndpointsWork() throws Exception {
        String body = """
                {
                  "resources": {
                    "mode": "MERGE",
                    "resources": [
                      {"id":"storage","kind":"storage","name":"Object Storage","metadata":{},"properties":{},"entitlementDefinitions":[]}
                    ]
                  }
                }
                """;
        mockMvc.perform(post("/api/tenants/acme/sync/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.summary.resourcesAdded").value(1));
        mockMvc.perform(post("/api/tenants/acme/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mockMvc.perform(get("/api/tenants/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources.storage.name").value("Object Storage"));
    }
}
