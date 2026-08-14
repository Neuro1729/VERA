package com.example.entitlements.persistence.postgres;

import com.example.entitlements.domain.EntitlementChangeType;
import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.persistence.EntitlementHistoryRepository;
import com.example.entitlements.persistence.JsonbConverter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class PostgresEntitlementHistoryRepository implements EntitlementHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonbConverter jsonb;

    public PostgresEntitlementHistoryRepository(NamedParameterJdbcTemplate jdbc, JsonbConverter jsonb) {
        this.jdbc = jdbc;
        this.jsonb = jsonb;
    }

    @Override
    public void append(EntitlementHistoryEvent event) {
        jdbc.update(
                """
                INSERT INTO entitlement_history (
                    id, tenant_id, resource_id, entitlement_key, target_type, target_id,
                    change_type, previous_grant_id, new_grant_id, old_value, new_value, changed_at)
                VALUES (
                    :id, :tenantId, :resourceId, :entitlementKey, :targetType, :targetId,
                    :changeType, :previousGrantId, :newGrantId, CAST(:oldValue AS jsonb),
                    CAST(:newValue AS jsonb), :changedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", event.id())
                        .addValue("tenantId", event.tenantId())
                        .addValue("resourceId", event.resourceId())
                        .addValue("entitlementKey", event.entitlementKey())
                        .addValue("targetType", event.target().type().name())
                        .addValue("targetId", event.target().id())
                        .addValue("changeType", event.changeType().name())
                        .addValue("previousGrantId", event.previousGrantId())
                        .addValue("newGrantId", event.newGrantId())
                        .addValue("oldValue", jsonb.writeValue(event.oldValue()))
                        .addValue("newValue", jsonb.writeValue(event.newValue()))
                        .addValue("changedAt", Timestamp.from(event.changedAt())));
    }

    @Override
    public List<EntitlementHistoryEvent> findByResource(String tenantId, String resourceId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, resource_id, entitlement_key, target_type, target_id,
                       change_type, previous_grant_id, new_grant_id, old_value, new_value, changed_at
                FROM entitlement_history
                WHERE tenant_id = :tenantId AND resource_id = :resourceId
                ORDER BY changed_at, id
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("resourceId", resourceId),
                this::mapEvent);
    }

    @Override
    public void clear() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE entitlement_history");
    }

    private EntitlementHistoryEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new EntitlementHistoryEvent(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("resource_id"),
                rs.getString("entitlement_key"),
                new Target(TargetType.valueOf(rs.getString("target_type")), rs.getString("target_id")),
                EntitlementChangeType.valueOf(rs.getString("change_type")),
                rs.getString("previous_grant_id"),
                rs.getString("new_grant_id"),
                jsonb.readValue(rs.getObject("old_value")),
                jsonb.readValue(rs.getObject("new_value")),
                rs.getTimestamp("changed_at").toInstant());
    }
}
