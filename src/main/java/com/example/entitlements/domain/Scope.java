package com.example.entitlements.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scope {
    private final String id;
    private String kind;
    private String name;
    private final Map<String, Object> metadata = new HashMap<>();
    private String parentScopeId;
    private final List<String> childScopeIds = new ArrayList<>();
    private final List<String> subjectIds = new ArrayList<>();

    public Scope(String id, String kind, String name, Map<String, Object> metadata, String parentScopeId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("scope id is required");
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("scope kind is required");
        this.id = id;
        this.kind = kind;
        this.name = (name == null || name.isBlank()) ? id : name;
        if (metadata != null) this.metadata.putAll(metadata);
        this.parentScopeId = parentScopeId;
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getName() { return name; }
    public Map<String, Object> getMetadata() { return Map.copyOf(metadata); }
    public String getParentScopeId() { return parentScopeId; }
    public List<String> getChildScopeIds() { return List.copyOf(childScopeIds); }
    public List<String> getSubjectIds() { return List.copyOf(subjectIds); }

    public void setKind(String kind) { if (kind != null && !kind.isBlank()) this.kind = kind; }
    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public void mergeMetadata(Map<String, Object> changes) { if (changes != null) metadata.putAll(changes); }
    public void replaceMetadata(Map<String, Object> replacement) {
        metadata.clear();
        if (replacement != null) metadata.putAll(replacement);
    }
    public void setParentScopeId(String parentScopeId) { this.parentScopeId = parentScopeId; }
    public void addChild(String scopeId) { if (!childScopeIds.contains(scopeId)) childScopeIds.add(scopeId); }
    public void removeChild(String scopeId) { childScopeIds.remove(scopeId); }
    public void addSubject(String subjectId) { if (!subjectIds.contains(subjectId)) subjectIds.add(subjectId); }
    public void removeSubject(String subjectId) { subjectIds.remove(subjectId); }
}
