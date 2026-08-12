package com.example.entitlements.domain;

import java.util.List;
import java.util.Map;

public record Resource(
        String id,
        String kind,
        String name,
        Map<String, Object> metadata,
        Map<String, EntitlementValue> properties,
        List<EntitlementDefinition> entitlementDefinitions
) {
    public Resource {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("resource id is required");
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("resource kind is required");
        if (name == null || name.isBlank()) name = id;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        entitlementDefinitions = entitlementDefinitions == null ? List.of() : List.copyOf(entitlementDefinitions);
    }

    public EntitlementDefinition definition(String key) {
        return entitlementDefinitions.stream()
                .filter(definition -> definition.key().equals(key))
                .findFirst()
                .orElse(null);
    }
}
