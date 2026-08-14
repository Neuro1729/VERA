package com.example.entitlements.api;

import com.example.entitlements.ResourceEntitlementApplication;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.RateLimitRequest;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.store.TenantRegistry;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ResourceEntitlementApplication.class)
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired TenantRegistry registry;
    @Autowired UsageStore usageStore;
    @Autowired RateLimitService rateLimitService;

    @BeforeEach
    void setUp() throws Exception {
        registry.clear();
        usageStore.clear();
        rateLimitService.clear();
        mockMvc.perform(post("/api/tenants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.registration())))
                .andExpect(status().isCreated());
    }

    @Test
    void registrationCanBeQueriedAsInMemoryOopState() throws Exception {
        mockMvc.perform(get("/api/tenants/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acme"))
                .andExpect(jsonPath("$.rootScopeId").value("root"))
                .andExpect(jsonPath("$.scopes.engineering.name").value("Engineering"))
                .andExpect(jsonPath("$.subjects.alice.scopeId").value("backend"));
    }

    @Test
    void evaluationEndpointUsesNearestEntitlement() throws Exception {
        EvaluationRequest request = new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(500));
        mockMvc.perform(post("/api/entitlements/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.grantId").value("g-eng-quota"))
                .andExpect(jsonPath("$.source.type").value("SCOPE"))
                .andExpect(jsonPath("$.source.id").value("engineering"));
    }

    @Test
    void consumptionEndpointSharesDepartmentPoolAcrossUsers() throws Exception {
        consume("alice", 100);
        mockMvc.perform(post("/api/entitlements/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new ConsumptionRequest("acme", "bob", "api", "api.requests", new BigDecimal("200")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.grantId").value("g-eng-quota"))
                .andExpect(jsonPath("$.consumed").value(300))
                .andExpect(jsonPath("$.remaining").value(999700));
    }

    @Test
    void commandEndpointCanChangeScopeEntitlementWithoutRestart() throws Exception {
        String command = """
                {
                  "type":"SET_ENTITLEMENT",
                  "tenantId":"acme",
                  "payload":{
                    "grantId":"backend-api",
                    "target":{"type":"SCOPE","id":"backend"},
                    "resourceId":"api",
                    "entitlementKey":"api.requests",
                    "value":{"type":"QUOTA","limit":42,"unit":"request","period":"MONTHLY"}
                  }
                }
                """;
        mockMvc.perform(post("/api/commands").contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        EvaluationRequest evaluate = new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(43));
        mockMvc.perform(post("/api/entitlements/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(evaluate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.grantId").value("backend-api"));
    }

    @Test
    void invalidCommandReturnsBadRequest() throws Exception {
        String command = "{\"type\":\"MOVE_SCOPE\",\"tenantId\":\"acme\",\"payload\":{\"scopeId\":\"engineering\",\"newParentScopeId\":\"backend\"}}";
        mockMvc.perform(post("/api/commands").contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unknownTenantReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/tenants/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateLimitEndpointSharesDepartmentBucketAcrossUsers() throws Exception {
        mockMvc.perform(post("/api/entitlements/rate-limit/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new RateLimitRequest("acme", "alice", "api", "api.rateLimit", new BigDecimal("30")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.grantId").value("g-eng-rate"))
                .andExpect(jsonPath("$.availableTokens").value(70));

        mockMvc.perform(post("/api/entitlements/rate-limit/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new RateLimitRequest("acme", "bob", "api", "api.rateLimit", new BigDecimal("20")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.grantId").value("g-eng-rate"))
                .andExpect(jsonPath("$.availableTokens").value(50));
    }

    @Test
    void resourceDistributionEndpointGroupsImmediateChildrenByWinningGrant() throws Exception {
        String setBackend = """
                {
                  "type":"SET_ENTITLEMENT",
                  "tenantId":"acme",
                  "payload":{
                    "grantId":"g-backend-hours",
                    "target":{"type":"SCOPE","id":"backend"},
                    "resourceId":"gpu",
                    "entitlementKey":"gpu.hours",
                    "value":{"type":"QUOTA","limit":8000,"unit":"hour","period":"MONTHLY"}
                  }
                }
                """;
        String setEng = """
                {
                  "type":"SET_ENTITLEMENT",
                  "tenantId":"acme",
                  "payload":{
                    "grantId":"g-eng-hours",
                    "target":{"type":"SCOPE","id":"engineering"},
                    "resourceId":"gpu",
                    "entitlementKey":"gpu.hours",
                    "value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}
                  }
                }
                """;
        mockMvc.perform(post("/api/commands").contentType(MediaType.APPLICATION_JSON).content(setEng))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/commands").contentType(MediaType.APPLICATION_JSON).content(setBackend))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tenants/acme/resources/gpu/distribution").param("scopeId", "engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("gpu"))
                .andExpect(jsonPath("$.scopeId").value("engineering"))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-eng-hours')].children[0].id").exists())
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-backend-hours')].children[0].id").value("backend"))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-eng-hours')].runtime.consumed").value(0))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-eng-hours')].runtime.remaining").value(5000));
    }

    @Test
    void resourceDistributionReturnsNotFoundForMissingTenantResourceOrScope() throws Exception {
        mockMvc.perform(get("/api/tenants/missing/resources/gpu/distribution").param("scopeId", "engineering"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tenants/acme/resources/missing/distribution").param("scopeId", "engineering"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tenants/acme/resources/gpu/distribution").param("scopeId", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resourceLiveEndpointShowsEntitledSubjectCounts() throws Exception {
        String setEng = """
                {
                  "type":"SET_ENTITLEMENT",
                  "tenantId":"acme",
                  "payload":{
                    "grantId":"g-eng-hours",
                    "target":{"type":"SCOPE","id":"engineering"},
                    "resourceId":"gpu",
                    "entitlementKey":"gpu.hours",
                    "value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}
                  }
                }
                """;
        String setBackend = """
                {
                  "type":"SET_ENTITLEMENT",
                  "tenantId":"acme",
                  "payload":{
                    "grantId":"g-backend-hours",
                    "target":{"type":"SCOPE","id":"backend"},
                    "resourceId":"gpu",
                    "entitlementKey":"gpu.hours",
                    "value":{"type":"QUOTA","limit":8000,"unit":"hour","period":"MONTHLY"}
                  }
                }
                """;
        mockMvc.perform(post("/api/commands").contentType(MediaType.APPLICATION_JSON).content(setEng))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/commands").contentType(MediaType.APPLICATION_JSON).content(setBackend))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tenants/acme/resources/gpu/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("gpu"))
                .andExpect(jsonPath("$.observedAt").exists())
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-eng-hours')].entitledSubjectCount").value(1))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-backend-hours')].entitledSubjectCount").value(2))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-eng-hours')].active").value(true))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementKey=='gpu.hours')].grants[?(@.grantId=='g-eng-hours')].runtime.consumed").value(0));
    }

    @Test
    void resourceLiveReturnsNotFoundForMissingTenantOrResource() throws Exception {
        mockMvc.perform(get("/api/tenants/missing/resources/gpu/live"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tenants/acme/resources/missing/live"))
                .andExpect(status().isNotFound());
    }

    private void consume(String subject, int amount) throws Exception {
        mockMvc.perform(post("/api/entitlements/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new ConsumptionRequest("acme", subject, "api", "api.requests", BigDecimal.valueOf(amount)))))
                .andExpect(status().isOk());
    }
}
