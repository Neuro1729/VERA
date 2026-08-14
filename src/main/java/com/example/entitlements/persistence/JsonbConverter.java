package com.example.entitlements.persistence;

import com.example.entitlements.domain.EntitlementValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JsonbConverter {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, EntitlementValue>> VALUE_MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public JsonbConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize jsonb", ex);
        }
    }

    public String writeValue(EntitlementValue value) {
        if (value == null) return null;
        try {
            return objectMapper.writerFor(EntitlementValue.class).writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize entitlement value", ex);
        }
    }

    public String writeValueMap(Map<String, EntitlementValue> values) {
        if (values == null) return "{}";
        try {
            return objectMapper.writerFor(VALUE_MAP).writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize entitlement map", ex);
        }
    }

    public Map<String, Object> readObjectMap(Object raw) {
        if (raw == null) return Map.of();
        try {
            return objectMapper.readValue(asJson(raw), OBJECT_MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to read jsonb object map", ex);
        }
    }

    public Map<String, EntitlementValue> readValueMap(Object raw) {
        if (raw == null) return Map.of();
        try {
            return objectMapper.readValue(asJson(raw), VALUE_MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to read jsonb entitlement map", ex);
        }
    }

    public EntitlementValue readValue(Object raw) {
        if (raw == null) return null;
        try {
            return objectMapper.readValue(asJson(raw), EntitlementValue.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to read entitlement value", ex);
        }
    }

    public JsonNode readTree(Object raw) {
        if (raw == null) return null;
        try {
            return objectMapper.readTree(asJson(raw));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to read jsonb tree", ex);
        }
    }

    private static String asJson(Object raw) {
        if (raw == null) return null;
        String typeName = raw.getClass().getName();
        if (typeName.equals("org.postgresql.util.PGobject")) {
            try {
                Object value = raw.getClass().getMethod("getValue").invoke(raw);
                return value == null ? null : value.toString();
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("failed to read PGobject", ex);
            }
        }
        return raw.toString();
    }
}
