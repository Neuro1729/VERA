package com.example.entitlements.request;

import java.time.Instant;

public record ApiKeyRotationResponse(String apiKey, String publicId, Instant rotatedAt) {}
