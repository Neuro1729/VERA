package com.example.entitlements.domain;

import java.util.Set;

public record SetValue(Set<String> values) implements EntitlementValue {
    public SetValue {
        values = values == null ? Set.of() : Set.copyOf(values);
    }

    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.SET;
    }
}
