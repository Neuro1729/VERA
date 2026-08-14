package com.example.entitlements.request;

public record AuthMeResponse(boolean authenticated, String tenantId, String email) {}
