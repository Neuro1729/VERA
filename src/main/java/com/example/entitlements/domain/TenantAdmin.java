package com.example.entitlements.domain;

import java.time.Instant;
import java.util.Locale;

public record TenantAdmin(
        String id,
        String tenantId,
        String email,
        String normalizedEmail,
        String passwordHash,
        Instant createdAt
) {
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String displayEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim();
    }
}
