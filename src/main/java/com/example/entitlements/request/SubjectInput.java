package com.example.entitlements.request;

import java.util.Map;

public record SubjectInput(String id, String kind, String name, Map<String, Object> metadata) {}
