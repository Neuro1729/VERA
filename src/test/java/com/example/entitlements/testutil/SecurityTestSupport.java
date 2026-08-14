package com.example.entitlements.testutil;

import com.example.entitlements.request.CompanySignupRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class SecurityTestSupport {
    private SecurityTestSupport() {}

    public static MockHttpServletRequestBuilder postCsrf(MockMvc mockMvc, String url) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = json(csrf, "token");
        Cookie xsrf = csrf.getResponse().getCookie("XSRF-TOKEN");
        MockHttpServletRequestBuilder request = post(url).header("X-XSRF-TOKEN", token);
        if (xsrf != null) {
            request.cookie(xsrf);
        }
        return request;
    }

    public static MvcResult signup(MockMvc mockMvc, ObjectMapper mapper, CompanySignupRequest request) throws Exception {
        return mockMvc.perform(postCsrf(mockMvc, "/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    public static MvcResult login(MockMvc mockMvc, ObjectMapper mapper, String email, String password) throws Exception {
        return mockMvc.perform(postCsrf(mockMvc, "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    public static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    public static String json(MvcResult result, String field) throws Exception {
        JsonNode node = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        return node.get(field).asText();
    }
}
