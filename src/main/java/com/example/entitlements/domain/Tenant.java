package com.example.entitlements.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class Tenant {
    private final String id;
    private String name;
    private String rootScopeId;
    private final Map<String, Scope> scopes = new LinkedHashMap<>();
    private final Map<String, Subject> subjects = new LinkedHashMap<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final Map<String, EntitlementGrant> grants = new LinkedHashMap<>();

    public Tenant(String id, String name) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("tenant id is required");
        this.id = id;
        this.name = (name == null || name.isBlank()) ? id : name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRootScopeId() { return rootScopeId; }
    public Map<String, Scope> getScopes() { return scopes; }
    public Map<String, Subject> getSubjects() { return subjects; }
    public Map<String, Resource> getResources() { return resources; }
    public Map<String, EntitlementGrant> getGrants() { return grants; }

    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public void setRootScopeId(String rootScopeId) { this.rootScopeId = rootScopeId; }
}
