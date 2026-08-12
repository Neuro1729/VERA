package com.example.entitlements.request;

import java.util.List;
import java.util.Map;

public record ScopeInput(
        String id,
        String kind,
        String name,
        Map<String, Object> metadata,
        List<ScopeInput> children,
        List<SubjectInput> subjects
) {}
