package com.example.entitlements.domain;

public record BooleanValue(boolean value) implements EntitlementValue {
    @Override
    public EntitlementValueType valueType() {
        return EntitlementValueType.BOOLEAN;
    }
}
