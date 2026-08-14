package com.example.entitlements.request;

public record CsrfTokenResponse(String token, String headerName, String parameterName) {}
