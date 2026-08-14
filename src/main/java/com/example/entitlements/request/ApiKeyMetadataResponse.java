package com.example.entitlements.request;

import java.time.Instant;

public record ApiKeyMetadataResponse(
        String publicId,
        String displayPrefix,
        Instant createdAt,
        Instant rotatedAt
) {}
