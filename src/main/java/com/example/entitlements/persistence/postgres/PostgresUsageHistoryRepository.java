package com.example.entitlements.persistence.postgres;

import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.UsageBucket;
import com.example.entitlements.domain.UsageEvent;
import com.example.entitlements.persistence.JsonbConverter;
import com.example.entitlements.persistence.UsageHistoryRepository;
import com.example.entitlements.store.UsageHistoryStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class PostgresUsageHistoryRepository implements UsageHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonbConverter jsonb;

    public PostgresUsageHistoryRepository(NamedParameterJdbcTemplate jdbc, JsonbConverter jsonb) {
        this.jdbc = jdbc;
        this.jsonb = jsonb;
    }

    @Override
    public void appendEvent(UsageEvent event) {
        jdbc.update(
                """
                INSERT INTO usage_events (
                    id, tenant_id, resource_id, resource_name_at_time, resource_kind_at_time,
                    entitlement_key, grant_id, grant_target_type, grant_target_id,
                    grant_target_name_at_time, subject_id, subject_name_at_time, used_value, occurred_at)
                VALUES (
                    :id, :tenantId, :resourceId, :resourceName, :resourceKind,
                    :entitlementKey, :grantId, :grantTargetType, :grantTargetId,
                    :grantTargetName, :subjectId, :subjectName, CAST(:usedValue AS jsonb), :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", event.id())
                        .addValue("tenantId", event.tenantId())
                        .addValue("resourceId", event.resourceId())
                        .addValue("resourceName", event.resourceNameAtTime())
                        .addValue("resourceKind", event.resourceKindAtTime())
                        .addValue("entitlementKey", event.entitlementKey())
                        .addValue("grantId", event.grantId())
                        .addValue("grantTargetType", event.grantTarget().type().name())
                        .addValue("grantTargetId", event.grantTarget().id())
                        .addValue("grantTargetName", event.grantTargetNameAtTime())
                        .addValue("subjectId", event.subjectId())
                        .addValue("subjectName", event.subjectNameAtTime())
                        .addValue("usedValue", jsonb.write(event.usedValue()))
                        .addValue("occurredAt", Timestamp.from(event.occurredAt())));
    }

    @Override
    public void addToBucket(
            String tenantId,
            String subjectId,
            String subjectNameAtTime,
            String resourceId,
            String resourceNameAtTime,
            String resourceKindAtTime,
            String entitlementKey,
            String grantId,
            Target grantTarget,
            String grantTargetNameAtTime,
            BigDecimal amount,
            Instant occurredAt
    ) {
        Instant bucketStart = UsageHistoryStore.bucketStart(occurredAt);
        Instant bucketEnd = UsageHistoryStore.bucketEnd(bucketStart);
        jdbc.update(
                """
                INSERT INTO usage_buckets (
                    tenant_id, subject_id, subject_name_at_time, resource_id, resource_name_at_time,
                    resource_kind_at_time, entitlement_key, grant_id, grant_target_type, grant_target_id,
                    grant_target_name_at_time, bucket_start, bucket_end, total_consumed, operation_count,
                    first_occurred_at, last_occurred_at)
                VALUES (
                    :tenantId, :subjectId, :subjectName, :resourceId, :resourceName, :resourceKind,
                    :entitlementKey, :grantId, :grantTargetType, :grantTargetId, :grantTargetName,
                    :bucketStart, :bucketEnd, :amount, 1, :occurredAt, :occurredAt)
                ON CONFLICT (
                    tenant_id, subject_id, resource_id, entitlement_key, grant_id, bucket_start)
                DO UPDATE SET
                    total_consumed = usage_buckets.total_consumed + EXCLUDED.total_consumed,
                    operation_count = usage_buckets.operation_count + EXCLUDED.operation_count,
                    first_occurred_at = LEAST(usage_buckets.first_occurred_at, EXCLUDED.first_occurred_at),
                    last_occurred_at = GREATEST(usage_buckets.last_occurred_at, EXCLUDED.last_occurred_at)
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("subjectId", subjectId)
                        .addValue("subjectName", subjectNameAtTime)
                        .addValue("resourceId", resourceId)
                        .addValue("resourceName", resourceNameAtTime)
                        .addValue("resourceKind", resourceKindAtTime)
                        .addValue("entitlementKey", entitlementKey)
                        .addValue("grantId", grantId)
                        .addValue("grantTargetType", grantTarget.type().name())
                        .addValue("grantTargetId", grantTarget.id())
                        .addValue("grantTargetName", grantTargetNameAtTime)
                        .addValue("bucketStart", Timestamp.from(bucketStart))
                        .addValue("bucketEnd", Timestamp.from(bucketEnd))
                        .addValue("amount", amount)
                        .addValue("occurredAt", Timestamp.from(occurredAt)));
    }

    @Override
    public List<UsageEvent> findEventsByResource(String tenantId, String resourceId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, resource_id, resource_name_at_time, resource_kind_at_time,
                       entitlement_key, grant_id, grant_target_type, grant_target_id,
                       grant_target_name_at_time, subject_id, subject_name_at_time, used_value, occurred_at
                FROM usage_events
                WHERE tenant_id = :tenantId AND resource_id = :resourceId
                ORDER BY occurred_at, id
                """,
                resourceParams(tenantId, resourceId),
                this::mapEvent);
    }

    @Override
    public List<UsageBucket> findBucketsByResource(String tenantId, String resourceId) {
        return jdbc.query(
                """
                SELECT tenant_id, subject_id, subject_name_at_time, resource_id, resource_name_at_time,
                       resource_kind_at_time, entitlement_key, grant_id, grant_target_type, grant_target_id,
                       grant_target_name_at_time, bucket_start, bucket_end, total_consumed, operation_count,
                       first_occurred_at, last_occurred_at
                FROM usage_buckets
                WHERE tenant_id = :tenantId AND resource_id = :resourceId
                ORDER BY bucket_start, grant_id, subject_id
                """,
                resourceParams(tenantId, resourceId),
                this::mapBucket);
    }

    @Override
    public boolean hasHistory(String tenantId, String resourceId) {
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usage_events WHERE tenant_id = :tenantId AND resource_id = :resourceId",
                resourceParams(tenantId, resourceId),
                Integer.class);
        if (events != null && events > 0) return true;
        Integer buckets = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usage_buckets WHERE tenant_id = :tenantId AND resource_id = :resourceId",
                resourceParams(tenantId, resourceId),
                Integer.class);
        return buckets != null && buckets > 0;
    }

    @Override
    public void clear() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE usage_events, usage_buckets");
    }

    private UsageEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new UsageEvent(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("resource_id"),
                rs.getString("resource_name_at_time"),
                rs.getString("resource_kind_at_time"),
                rs.getString("entitlement_key"),
                rs.getString("grant_id"),
                new Target(TargetType.valueOf(rs.getString("grant_target_type")), rs.getString("grant_target_id")),
                rs.getString("grant_target_name_at_time"),
                rs.getString("subject_id"),
                rs.getString("subject_name_at_time"),
                jsonb.readTree(rs.getObject("used_value")),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private UsageBucket mapBucket(ResultSet rs, int rowNum) throws SQLException {
        return new UsageBucket(
                rs.getString("tenant_id"),
                rs.getString("subject_id"),
                rs.getString("subject_name_at_time"),
                rs.getString("resource_id"),
                rs.getString("resource_name_at_time"),
                rs.getString("resource_kind_at_time"),
                rs.getString("entitlement_key"),
                rs.getString("grant_id"),
                new Target(TargetType.valueOf(rs.getString("grant_target_type")), rs.getString("grant_target_id")),
                rs.getString("grant_target_name_at_time"),
                rs.getTimestamp("bucket_start").toInstant(),
                rs.getTimestamp("bucket_end").toInstant(),
                rs.getBigDecimal("total_consumed"),
                rs.getLong("operation_count"),
                rs.getTimestamp("first_occurred_at").toInstant(),
                rs.getTimestamp("last_occurred_at").toInstant());
    }

    private static MapSqlParameterSource resourceParams(String tenantId, String resourceId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("resourceId", resourceId);
    }
}
