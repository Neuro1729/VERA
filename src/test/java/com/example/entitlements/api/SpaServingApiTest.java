package com.example.entitlements.api;

import com.example.entitlements.ResourceEntitlementApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ResourceEntitlementApplication.class, properties = "vera.security.enabled=true")
@AutoConfigureMockMvc
class SpaServingApiTest {
    @Autowired MockMvc mockMvc;

    @Test
    void spaRoutesArePublicAndServeIndex() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vera-spa-test")));
        mockMvc.perform(get("/workspace/resources"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vera-spa-test")));
    }

    @Test
    void staticAssetsArePublic() throws Exception {
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vera-asset-test")));
    }

    @Test
    void apiStaysProtected() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingAssetIsNotSpaFallback() throws Exception {
        mockMvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }
}
