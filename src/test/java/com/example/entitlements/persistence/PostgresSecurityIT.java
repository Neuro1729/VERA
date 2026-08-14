package com.example.entitlements.persistence;

import com.example.entitlements.service.CompanySignupService;
import com.example.entitlements.testutil.SecurityTestSupport;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "vera.security.enabled=true")
class PostgresSecurityIT extends PostgresIntegrationTest {
    @Autowired ObjectMapper mapper;
    @Autowired CompanySignupService signupService;

    @Test
    void adminAndCredentialHashesSurviveReload() throws Exception {
        MvcResult signup = SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        String apiKey = SecurityTestSupport.json(signup, "apiKey");
        String hash = jdbc.queryForObject(
                "SELECT secret_hash FROM tenant_api_credential WHERE tenant_id = 'acme'", String.class);
        assertFalse(apiKey.equals(hash));
        assertTrue(hash != null && hash.startsWith("$2"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_api_credential WHERE secret_hash = ?", Integer.class, apiKey));

        reload("acme");
        SecurityTestSupport.login(mockMvc, mapper, "admin@acme.com", "a-long-password");
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"alice\",\"resourceId\":\"api\",\"entitlementKey\":\"api.enabled\",\"requestedValue\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void signupFailureRollsBackTenantAdminAndCredential() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_credential() RETURNS trigger AS $$
                BEGIN
                  RAISE EXCEPTION 'forced credential failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER trg_fail_credential
                BEFORE INSERT ON tenant_api_credential
                FOR EACH ROW EXECUTE FUNCTION fail_credential()
                """);
        try {
            assertThrows(RuntimeException.class, () -> signupService.signup(TestFixtures.signup()));
            assertTrue(registry.all().isEmpty());
            assertFalse(tenantRepository.existsById("acme"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM scopes", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM subjects", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM resources", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM entitlement_grants", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM entitlement_history", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tenant_admin", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tenant_api_credential", Integer.class));
            assertThrows(Exception.class, () -> registry.getRequired("acme"));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_credential ON tenant_api_credential");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_credential()");
        }
    }
}
