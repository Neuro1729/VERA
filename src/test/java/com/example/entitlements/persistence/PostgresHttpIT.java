package com.example.entitlements.persistence;

import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.RateLimitRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostgresHttpIT extends PostgresIntegrationTest {
    @Autowired ObjectMapper mapper;

    @Test
    void httpApisWorkFromPersistedReloadedState() throws Exception {
        mockMvc.perform(post("/api/tenants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(com.example.entitlements.testutil.TestFixtures.registration())))
                .andExpect(status().isCreated());

        reload("acme");

        mockMvc.perform(get("/api/tenants/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acme"))
                .andExpect(jsonPath("$.subjects.alice.scopeId").value("backend"));

        mockMvc.perform(post("/api/entitlements/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new EvaluationRequest("acme", "alice", "api", "api.requests", mapper.valueToTree(500)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.grantId").value("g-eng-quota"));

        mockMvc.perform(post("/api/entitlements/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new ConsumptionRequest("acme", "alice", "api", "api.requests", new BigDecimal("40")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.consumed").value(40));

        mockMvc.perform(post("/api/entitlements/rate-limit/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new RateLimitRequest("acme", "alice", "api", "api.rateLimit", new BigDecimal("5")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/entitlements/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                new EvaluationRequest("acme", "alice", "api", "api.models", mapper.valueToTree("large")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        String command = """
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
        mockMvc.perform(post("/api/commands").contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        reload("acme");

        mockMvc.perform(get("/api/tenants/acme/resources/gpu/distribution").param("scopeId", "engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("gpu"));
        mockMvc.perform(get("/api/tenants/acme/resources/gpu/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("gpu"));
        mockMvc.perform(get("/api/tenants/acme/resources/gpu/entitlement-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitlements[1].changes[0].changeType").value("CREATED"));
        mockMvc.perform(get("/api/tenants/acme/resources/api/usage-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("api"))
                .andExpect(jsonPath("$.entitlements[0].grants[0].usage[0].type").value("BUCKET"));
    }
}
