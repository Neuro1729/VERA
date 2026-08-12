package com.example.entitlements.domain;

import java.util.Objects;

public record Target(TargetType type, String id) {
    public Target {
        Objects.requireNonNull(type, "target type is required");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("target id is required");
        }
    }
}
