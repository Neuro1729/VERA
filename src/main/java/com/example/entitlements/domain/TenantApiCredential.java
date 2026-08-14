package com.example.entitlements.domain;

import java.time.Instant;

public record TenantApiCredential(
        String id,
        String tenantId,
        String publicId,
        String secretHash,
        boolean enabled,
        Instant createdAt,
        Instant rotatedAt
) {
    public TenantApiCredential rotated(String newPublicId, String newSecretHash, Instant rotatedAt) {
        return new TenantApiCredential(id, tenantId, newPublicId, newSecretHash, enabled, createdAt, rotatedAt);
    }
}
