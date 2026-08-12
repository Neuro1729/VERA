package com.example.entitlements.request;

import com.fasterxml.jackson.databind.JsonNode;

public record CommandRequest(CommandType type, String tenantId, JsonNode payload) {}
