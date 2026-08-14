package com.example.entitlements.api;

import com.example.entitlements.ResourceEntitlementApplication;
import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.domain.TenantApiCredential;
import com.example.entitlements.persistence.TenantAdminRepository;
import com.example.entitlements.persistence.TenantApiCredentialRepository;
import com.example.entitlements.service.ApiKeyService;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageHistoryStore;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.SecurityTestSupport;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ResourceEntitlementApplication.class, properties = "vera.security.enabled=true")
@AutoConfigureMockMvc
class AuthApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired TenantRegistry registry;
    @Autowired UsageStore usageStore;
    @Autowired EntitlementHistoryStore historyStore;
    @Autowired UsageHistoryStore usageHistoryStore;
    @Autowired RateLimitService rateLimitService;
    @Autowired TenantAdminRepository adminRepository;
    @Autowired TenantApiCredentialRepository credentialRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired GrantResolutionCache cache;

    @BeforeEach
    void setUp() {
        registry.clear();
        usageStore.clear();
        historyStore.clear();
        usageHistoryStore.clear();
        rateLimitService.clear();
        adminRepository.clear();
        credentialRepository.clear();
        cache.clear();
    }

    @Test
    void signupCreatesTenantAdminCredentialAndSession() throws Exception {
        MvcResult result = SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        String apiKey = SecurityTestSupport.json(result, "apiKey");

        mockMvc.perform(get("/api/tenants/acme").session(SecurityTestSupport.session(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acme"));
        assertTrue(adminRepository.existsByNormalizedEmail("admin@acme.com"));
        TenantApiCredential stored = credentialRepository.findByTenantId("acme").orElseThrow();
        ApiKeyService.ParsedKey parsed = ApiKeyService.parse(apiKey);
        assertNotNull(parsed);
        assertNotEquals(apiKey, stored.secretHash());
        assertFalse(stored.secretHash().contains(parsed.secret()));
        assertTrue(passwordEncoder.matches(parsed.secret(), stored.secretHash()));
        mockMvc.perform(get("/api/auth/me").session(SecurityTestSupport.session(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.email").value("admin@acme.com"));
    }

    @Test
    void duplicateTenantAndEmailAreRejected() throws Exception {
        SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                TestFixtures.signup("acme", "Acme Corp", "other@acme.com", "a-long-password"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                TestFixtures.signup("globex", "Globex", "ADMIN@acme.com", "a-long-password"))))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(
                                TestFixtures.signup("acme", "Acme Corp", "admin@acme.com", "short-pass"))))
                .andExpect(status().isBadRequest());
        assertTrue(registry.all().isEmpty());
        assertFalse(adminRepository.existsByNormalizedEmail("admin@acme.com"));
    }

    @Test
    void loginIsNormalizedAndCreatesSession() throws Exception {
        SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        MvcResult login = SecurityTestSupport.login(mockMvc, mapper, "  Admin@Acme.com ", "a-long-password");
        mockMvc.perform(get("/api/auth/me").session(SecurityTestSupport.session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.email").value("admin@acme.com"));
    }

    @Test
    void loginFailuresAreUnauthorizedWithoutLeakingWhichField() throws Exception {
        SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@acme.com\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@acme.com\",\"password\":\"a-long-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    @Test
    void meRequiresSessionAndLogoutInvalidatesIt() throws Exception {
        SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        MockHttpSession session = SecurityTestSupport.session(
                SecurityTestSupport.login(mockMvc, mapper, "admin@acme.com", "a-long-password"));
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/auth/logout").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tenants/acme").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfIsRequiredForMutatingAdminRequestsAndReturnedByEndpoint() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@acme.com\",\"password\":\"a-long-password\"}"))
                .andExpect(status().isForbidden());

        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String token = mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        Cookie xsrf = csrf.getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/auth/signup")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.signup())))
                .andExpect(status().isCreated());

        csrf = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        token = mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        xsrf = csrf.getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/auth/login")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@acme.com\",\"password\":\"a-long-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void apiKeyMetadataNeverRevealsSecretAndRotationReplacesKey() throws Exception {
        MvcResult signup = SecurityTestSupport.signup(mockMvc, mapper, TestFixtures.signup());
        String oldKey = SecurityTestSupport.json(signup, "apiKey");
        MockHttpSession session = SecurityTestSupport.session(signup);

        mockMvc.perform(get("/api/auth/api-key").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").exists())
                .andExpect(jsonPath("$.displayPrefix").exists())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.secretHash").doesNotExist());

        MvcResult rotated = mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/auth/api-key/rotate")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"globex\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").exists())
                .andReturn();
        String newKey = SecurityTestSupport.json(rotated, "apiKey");
        assertNotEquals(oldKey, newKey);

        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", newKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"alice\",\"resourceId\":\"api\",\"entitlementKey\":\"api.enabled\",\"requestedValue\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mockMvc.perform(post("/api/gateway/tenants/acme/evaluate")
                        .header("X-VERA-API-KEY", oldKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"alice\",\"resourceId\":\"api\",\"entitlementKey\":\"api.enabled\",\"requestedValue\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRegistrationApplyIsDeniedWhilePreviewStaysPublic() throws Exception {
        mockMvc.perform(post("/api/company-registration/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.companyRegistration())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/company-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.companyRegistration())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(SecurityTestSupport.postCsrf(mockMvc, "/api/tenants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(TestFixtures.registration())))
                .andExpect(status().isUnauthorized());
    }
}
