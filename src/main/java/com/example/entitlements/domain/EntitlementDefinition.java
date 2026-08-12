package com.example.entitlements.domain;

import java.util.Objects;

public record EntitlementDefinition(String key, String name, EntitlementValueType valueType) {
    public EntitlementDefinition {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("entitlement key is required");
        if (name == null || name.isBlank()) name = key;
        Objects.requireNonNull(valueType, "entitlement valueType is required");
    }
}
