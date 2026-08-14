package com.example.entitlements.persistence.postgres;

import com.example.entitlements.domain.EntitlementDefinition;
import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.EntitlementValueType;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.persistence.JsonbConverter;
import com.example.entitlements.persistence.PersistenceExceptions;
import com.example.entitlements.persistence.TenantRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PostgresTenantRepository implements TenantRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonbConverter jsonb;

    public PostgresTenantRepository(NamedParameterJdbcTemplate jdbc, JsonbConverter jsonb) {
        this.jdbc = jdbc;
        this.jsonb = jsonb;
    }

    @Override
    public void insert(Tenant tenant) {
        try {
            jdbc.update(
                    "INSERT INTO tenants (id, name, root_scope_id) VALUES (:id, :name, :rootScopeId)",
                    params()
                            .addValue("id", tenant.getId())
                            .addValue("name", tenant.getName())
                            .addValue("rootScopeId", tenant.getRootScopeId()));
            insertScopesInParentOrder(tenant);
            for (Subject subject : tenant.getSubjects().values()) {
                insertSubject(tenant.getId(), subject);
            }
            for (Resource resource : tenant.getResources().values()) {
                insertResource(tenant.getId(), resource);
            }
            for (EntitlementGrant grant : tenant.getGrants().values()) {
                upsertGrant(tenant.getId(), grant);
            }
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public Optional<Tenant> findById(String tenantId) {
        List<Tenant> tenants = jdbc.query(
                "SELECT id, name, root_scope_id FROM tenants WHERE id = :id",
                params().addValue("id", tenantId),
                (rs, rowNum) -> {
                    Tenant loaded = new Tenant(rs.getString("id"), rs.getString("name"));
                    loaded.setRootScopeId(rs.getString("root_scope_id"));
                    return loaded;
                });
        if (tenants.isEmpty()) return Optional.empty();
        Tenant tenant = tenants.getFirst();
        loadScopes(tenant);
        loadSubjects(tenant);
        loadResources(tenant);
        loadGrants(tenant);
        return Optional.of(tenant);
    }

    @Override
    public boolean existsById(String tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = :id",
                params().addValue("id", tenantId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<String> findAllIds() {
        return jdbc.query("SELECT id FROM tenants ORDER BY id", (rs, rowNum) -> rs.getString("id"));
    }

    @Override
    public void insertScope(String tenantId, Scope scope) {
        try {
            jdbc.update(
                    """
                    INSERT INTO scopes (tenant_id, id, kind, name, parent_scope_id, metadata)
                    VALUES (:tenantId, :id, :kind, :name, :parentScopeId, CAST(:metadata AS jsonb))
                    """,
                    scopeParams(tenantId, scope));
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public void updateScope(String tenantId, Scope scope) {
        jdbc.update(
                """
                UPDATE scopes
                SET kind = :kind, name = :name, metadata = CAST(:metadata AS jsonb)
                WHERE tenant_id = :tenantId AND id = :id
                """,
                scopeParams(tenantId, scope));
    }

    @Override
    public void updateScopeParent(String tenantId, String scopeId, String parentScopeId) {
        jdbc.update(
                """
                UPDATE scopes SET parent_scope_id = :parentScopeId
                WHERE tenant_id = :tenantId AND id = :id
                """,
                params()
                        .addValue("tenantId", tenantId)
                        .addValue("id", scopeId)
                        .addValue("parentScopeId", parentScopeId));
    }

    @Override
    public void deleteScopes(String tenantId, Collection<String> scopeIds) {
        if (scopeIds == null || scopeIds.isEmpty()) return;
        jdbc.update(
                "DELETE FROM scopes WHERE tenant_id = :tenantId AND id IN (:ids)",
                params().addValue("tenantId", tenantId).addValue("ids", List.copyOf(scopeIds)));
    }

    @Override
    public void insertSubject(String tenantId, Subject subject) {
        try {
            jdbc.update(
                    """
                    INSERT INTO subjects (tenant_id, id, kind, name, scope_id, metadata)
                    VALUES (:tenantId, :id, :kind, :name, :scopeId, CAST(:metadata AS jsonb))
                    """,
                    subjectParams(tenantId, subject));
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public void updateSubject(String tenantId, Subject subject) {
        jdbc.update(
                """
                UPDATE subjects
                SET kind = :kind, name = :name, metadata = CAST(:metadata AS jsonb)
                WHERE tenant_id = :tenantId AND id = :id
                """,
                subjectParams(tenantId, subject));
    }

    @Override
    public void updateSubjectScope(String tenantId, String subjectId, String scopeId) {
        jdbc.update(
                """
                UPDATE subjects SET scope_id = :scopeId
                WHERE tenant_id = :tenantId AND id = :id
                """,
                params().addValue("tenantId", tenantId).addValue("id", subjectId).addValue("scopeId", scopeId));
    }

    @Override
    public void deleteSubjects(String tenantId, Collection<String> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) return;
        jdbc.update(
                "DELETE FROM subjects WHERE tenant_id = :tenantId AND id IN (:ids)",
                params().addValue("tenantId", tenantId).addValue("ids", List.copyOf(subjectIds)));
    }

    @Override
    public void insertResource(String tenantId, Resource resource) {
        try {
            writeResource(tenantId, resource, true);
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public void updateResource(String tenantId, Resource resource) {
        writeResource(tenantId, resource, false);
    }

    @Override
    public void deleteResource(String tenantId, String resourceId) {
        jdbc.update(
                "DELETE FROM resources WHERE tenant_id = :tenantId AND id = :id",
                params().addValue("tenantId", tenantId).addValue("id", resourceId));
    }

    @Override
    public void upsertGrant(String tenantId, EntitlementGrant grant) {
        try {
            jdbc.update(
                    """
                    INSERT INTO entitlement_grants (
                        tenant_id, id, target_type, target_id, resource_id, entitlement_key,
                        value_type, value_json, created_at, updated_at)
                    VALUES (
                        :tenantId, :id, :targetType, :targetId, :resourceId, :entitlementKey,
                        :valueType, CAST(:valueJson AS jsonb), now(), now())
                    ON CONFLICT (tenant_id, id) DO UPDATE SET
                        target_type = EXCLUDED.target_type,
                        target_id = EXCLUDED.target_id,
                        resource_id = EXCLUDED.resource_id,
                        entitlement_key = EXCLUDED.entitlement_key,
                        value_type = EXCLUDED.value_type,
                        value_json = EXCLUDED.value_json,
                        updated_at = now()
                    """,
                    grantParams(tenantId, grant));
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public void deleteGrant(String tenantId, String grantId) {
        jdbc.update(
                "DELETE FROM entitlement_grants WHERE tenant_id = :tenantId AND id = :id",
                params().addValue("tenantId", tenantId).addValue("id", grantId));
    }

    @Override
    public void deleteGrants(String tenantId, Collection<String> grantIds) {
        if (grantIds == null || grantIds.isEmpty()) return;
        jdbc.update(
                "DELETE FROM entitlement_grants WHERE tenant_id = :tenantId AND id IN (:ids)",
                params().addValue("tenantId", tenantId).addValue("ids", List.copyOf(grantIds)));
    }

    @Override
    public void clear() {
        jdbc.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    tenant_admin,
                    tenant_api_credential,
                    usage_events,
                    usage_buckets,
                    entitlement_history,
                    usage_current,
                    rate_limit_state,
                    entitlement_grants,
                    entitlement_definitions,
                    subjects,
                    scopes,
                    resources,
                    tenants
                RESTART IDENTITY CASCADE
                """);
    }

    private void insertScopesInParentOrder(Tenant tenant) {
        List<Scope> ordered = new ArrayList<>();
        collectScopes(tenant, tenant.getRootScopeId(), ordered);
        for (Scope scope : tenant.getScopes().values()) {
            if (!ordered.contains(scope)) ordered.add(scope);
        }
        for (Scope scope : ordered) {
            insertScope(tenant.getId(), scope);
        }
    }

    private void collectScopes(Tenant tenant, String scopeId, List<Scope> ordered) {
        if (scopeId == null) return;
        Scope scope = tenant.getScopes().get(scopeId);
        if (scope == null || ordered.contains(scope)) return;
        ordered.add(scope);
        for (String childId : scope.getChildScopeIds()) {
            collectScopes(tenant, childId, ordered);
        }
    }

    private void loadScopes(Tenant tenant) {
        List<Scope> scopes = jdbc.query(
                """
                SELECT id, kind, name, parent_scope_id, metadata
                FROM scopes
                WHERE tenant_id = :tenantId
                ORDER BY created_order
                """,
                params().addValue("tenantId", tenant.getId()),
                this::mapScope);
        for (Scope scope : scopes) {
            tenant.getScopes().put(scope.getId(), scope);
        }
        for (Scope scope : scopes) {
            String parentId = scope.getParentScopeId();
            if (parentId == null) continue;
            Scope parent = tenant.getScopes().get(parentId);
            if (parent != null) parent.addChild(scope.getId());
        }
    }

    private void loadSubjects(Tenant tenant) {
        List<Subject> subjects = jdbc.query(
                """
                SELECT id, kind, name, scope_id, metadata
                FROM subjects
                WHERE tenant_id = :tenantId
                ORDER BY created_order
                """,
                params().addValue("tenantId", tenant.getId()),
                this::mapSubject);
        for (Subject subject : subjects) {
            tenant.getSubjects().put(subject.getId(), subject);
            Scope scope = tenant.getScopes().get(subject.getScopeId());
            if (scope != null) scope.addSubject(subject.getId());
        }
    }

    private void loadResources(Tenant tenant) {
        List<ResourceRow> rows = jdbc.query(
                """
                SELECT id, kind, name, metadata, properties
                FROM resources
                WHERE tenant_id = :tenantId
                ORDER BY created_order
                """,
                params().addValue("tenantId", tenant.getId()),
                this::mapResourceRow);
        Map<String, List<EntitlementDefinition>> definitions = loadDefinitions(tenant.getId());
        for (ResourceRow row : rows) {
            tenant.getResources().put(row.id(), new Resource(
                    row.id(),
                    row.kind(),
                    row.name(),
                    row.metadata(),
                    row.properties(),
                    definitions.getOrDefault(row.id(), List.of())));
        }
    }

    private Map<String, List<EntitlementDefinition>> loadDefinitions(String tenantId) {
        Map<String, List<EntitlementDefinition>> byResource = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT resource_id, entitlement_key, name, value_type
                FROM entitlement_definitions
                WHERE tenant_id = :tenantId
                ORDER BY resource_id, position, entitlement_key
                """,
                params().addValue("tenantId", tenantId),
                rs -> {
                    String resourceId = rs.getString("resource_id");
                    byResource.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(
                            new EntitlementDefinition(
                                    rs.getString("entitlement_key"),
                                    rs.getString("name"),
                                    EntitlementValueType.valueOf(rs.getString("value_type"))));
                });
        return byResource;
    }

    private void loadGrants(Tenant tenant) {
        jdbc.query(
                """
                SELECT id, target_type, target_id, resource_id, entitlement_key, value_json
                FROM entitlement_grants
                WHERE tenant_id = :tenantId
                ORDER BY created_at, id
                """,
                params().addValue("tenantId", tenant.getId()),
                rs -> {
                    tenant.putGrant(mapGrant(rs));
                });
    }

    private void writeResource(String tenantId, Resource resource, boolean insert) {
        MapSqlParameterSource params = params()
                .addValue("tenantId", tenantId)
                .addValue("id", resource.id())
                .addValue("kind", resource.kind())
                .addValue("name", resource.name())
                .addValue("metadata", jsonb.write(resource.metadata()))
                .addValue("properties", jsonb.writeValueMap(resource.properties()));
        if (insert) {
            jdbc.update(
                    """
                    INSERT INTO resources (tenant_id, id, kind, name, metadata, properties)
                    VALUES (:tenantId, :id, :kind, :name, CAST(:metadata AS jsonb), CAST(:properties AS jsonb))
                    """,
                    params);
        } else {
            jdbc.update(
                    """
                    UPDATE resources
                    SET kind = :kind, name = :name, metadata = CAST(:metadata AS jsonb),
                        properties = CAST(:properties AS jsonb)
                    WHERE tenant_id = :tenantId AND id = :id
                    """,
                    params);
            jdbc.update(
                    "DELETE FROM entitlement_definitions WHERE tenant_id = :tenantId AND resource_id = :id",
                    params);
        }
        int position = 0;
        for (EntitlementDefinition definition : resource.entitlementDefinitions()) {
            jdbc.update(
                    """
                    INSERT INTO entitlement_definitions (
                        tenant_id, resource_id, entitlement_key, name, value_type, position)
                    VALUES (:tenantId, :resourceId, :key, :name, :valueType, :position)
                    """,
                    params()
                            .addValue("tenantId", tenantId)
                            .addValue("resourceId", resource.id())
                            .addValue("key", definition.key())
                            .addValue("name", definition.name())
                            .addValue("valueType", definition.valueType().name())
                            .addValue("position", position++));
        }
    }

    private Scope mapScope(ResultSet rs, int rowNum) throws SQLException {
        return new Scope(
                rs.getString("id"),
                rs.getString("kind"),
                rs.getString("name"),
                jsonb.readObjectMap(rs.getObject("metadata")),
                rs.getString("parent_scope_id"));
    }

    private Subject mapSubject(ResultSet rs, int rowNum) throws SQLException {
        return new Subject(
                rs.getString("id"),
                rs.getString("kind"),
                rs.getString("name"),
                jsonb.readObjectMap(rs.getObject("metadata")),
                rs.getString("scope_id"));
    }

    private ResourceRow mapResourceRow(ResultSet rs, int rowNum) throws SQLException {
        return new ResourceRow(
                rs.getString("id"),
                rs.getString("kind"),
                rs.getString("name"),
                jsonb.readObjectMap(rs.getObject("metadata")),
                jsonb.readValueMap(rs.getObject("properties")));
    }

    private EntitlementGrant mapGrant(ResultSet rs) throws SQLException {
        return new EntitlementGrant(
                rs.getString("id"),
                new Target(TargetType.valueOf(rs.getString("target_type")), rs.getString("target_id")),
                rs.getString("resource_id"),
                rs.getString("entitlement_key"),
                jsonb.readValue(rs.getObject("value_json")));
    }

    private MapSqlParameterSource scopeParams(String tenantId, Scope scope) {
        return params()
                .addValue("tenantId", tenantId)
                .addValue("id", scope.getId())
                .addValue("kind", scope.getKind())
                .addValue("name", scope.getName())
                .addValue("parentScopeId", scope.getParentScopeId())
                .addValue("metadata", jsonb.write(scope.getMetadata()));
    }

    private MapSqlParameterSource subjectParams(String tenantId, Subject subject) {
        return params()
                .addValue("tenantId", tenantId)
                .addValue("id", subject.getId())
                .addValue("kind", subject.getKind())
                .addValue("name", subject.getName())
                .addValue("scopeId", subject.getScopeId())
                .addValue("metadata", jsonb.write(subject.getMetadata()));
    }

    private MapSqlParameterSource grantParams(String tenantId, EntitlementGrant grant) {
        return params()
                .addValue("tenantId", tenantId)
                .addValue("id", grant.id())
                .addValue("targetType", grant.target().type().name())
                .addValue("targetId", grant.target().id())
                .addValue("resourceId", grant.resourceId())
                .addValue("entitlementKey", grant.entitlementKey())
                .addValue("valueType", grant.value().valueType().name())
                .addValue("valueJson", jsonb.writeValue(grant.value()));
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    private record ResourceRow(
            String id,
            String kind,
            String name,
            Map<String, Object> metadata,
            Map<String, EntitlementValue> properties
    ) {}
}
