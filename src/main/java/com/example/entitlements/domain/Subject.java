package com.example.entitlements.domain;

import java.util.HashMap;
import java.util.Map;

public class Subject {
    private final String id;
    private String kind;
    private String name;
    private final Map<String, Object> metadata = new HashMap<>();
    private String scopeId;

    public Subject(String id, String kind, String name, Map<String, Object> metadata, String scopeId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("subject id is required");
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("subject kind is required");
        this.id = id;
        this.kind = kind;
        this.name = (name == null || name.isBlank()) ? id : name;
        if (metadata != null) this.metadata.putAll(metadata);
        this.scopeId = scopeId;
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getName() { return name; }
    public Map<String, Object> getMetadata() { return Map.copyOf(metadata); }
    public String getScopeId() { return scopeId; }

    public void setKind(String kind) { if (kind != null && !kind.isBlank()) this.kind = kind; }
    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public void mergeMetadata(Map<String, Object> changes) { if (changes != null) metadata.putAll(changes); }
    public void replaceMetadata(Map<String, Object> replacement) {
        metadata.clear();
        if (replacement != null) metadata.putAll(replacement);
    }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
}
