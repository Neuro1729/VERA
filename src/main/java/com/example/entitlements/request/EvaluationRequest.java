package com.example.entitlements.request;

import com.fasterxml.jackson.databind.JsonNode;

public record EvaluationRequest(
        String tenantId,
        String subjectId,
        String resourceId,
        String entitlementKey,
        JsonNode requestedValue
) {}
