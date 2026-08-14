package com.example.entitlements.security;

import com.example.entitlements.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;

final class SecurityResponses {
    private SecurityResponses() {}

    static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
