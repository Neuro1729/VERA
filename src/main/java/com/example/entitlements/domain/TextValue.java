package com.example.entitlements.domain;

import java.util.Objects;

public record TextValue(String value) implements EntitlementValue {
    public TextValue {
        Objects.requireNonNull(value, "text value is required");
    }

    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.TEXT;
    }
}
