package com.example.entitlements.persistence.postgres;

import com.example.entitlements.domain.Usage;
import com.example.entitlements.persistence.UsageRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class PostgresUsageRepository implements UsageRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresUsageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Usage get(String tenantId, String grantId) {
        return queryOne(tenantId, grantId, false);
    }

    @Override
    public Usage lock(String tenantId, String grantId) {
        ensureRowLockable(tenantId, grantId);
        return queryOne(tenantId, grantId, true);
    }

    @Override
    public void save(String tenantId, Usage usage) {
        jdbc.update(
                """
                INSERT INTO usage_current (tenant_id, grant_id, consumed, period_start, period_end, version)
                VALUES (:tenantId, :grantId, :consumed, :periodStart, :periodEnd, 0)
                ON CONFLICT (tenant_id, grant_id) DO UPDATE SET
                    consumed = EXCLUDED.consumed,
                    period_start = EXCLUDED.period_start,
                    period_end = EXCLUDED.period_end,
                    version = usage_current.version + 1
                """,
                params(tenantId, usage.getGrantId())
                        .addValue("consumed", usage.getConsumed())
                        .addValue("periodStart", Timestamp.from(usage.getPeriodStart()))
                        .addValue("periodEnd", Timestamp.from(usage.getPeriodEnd())));
    }

    @Override
    public void remove(String tenantId, String grantId) {
        jdbc.update(
                "DELETE FROM usage_current WHERE tenant_id = :tenantId AND grant_id = :grantId",
                params(tenantId, grantId));
    }

    @Override
    public Collection<Usage> findAllByTenant(String tenantId) {
        return jdbc.query(
                """
                SELECT grant_id, consumed, period_start, period_end
                FROM usage_current
                WHERE tenant_id = :tenantId
                """,
                new MapSqlParameterSource("tenantId", tenantId),
                this::mapUsage);
    }

    @Override
    public Collection<Usage> all() {
        return jdbc.query(
                "SELECT grant_id, consumed, period_start, period_end FROM usage_current",
                this::mapUsage);
    }

    @Override
    public void clear() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE usage_current");
    }

    /**
     * Inserts a placeholder row so concurrent first-use requests can {@code SELECT ... FOR UPDATE}
     * the same grant. Callers overwrite consumed/window on save.
     */
    private void ensureRowLockable(String tenantId, String grantId) {
        Instant epoch = Instant.EPOCH;
        jdbc.update(
                """
                INSERT INTO usage_current (tenant_id, grant_id, consumed, period_start, period_end, version)
                VALUES (:tenantId, :grantId, 0, :epoch, :epoch, 0)
                ON CONFLICT (tenant_id, grant_id) DO NOTHING
                """,
                params(tenantId, grantId).addValue("epoch", Timestamp.from(epoch)));
    }

    private Usage queryOne(String tenantId, String grantId, boolean forUpdate) {
        String sql = """
                SELECT grant_id, consumed, period_start, period_end
                FROM usage_current
                WHERE tenant_id = :tenantId AND grant_id = :grantId
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<Usage> found = jdbc.query(sql, params(tenantId, grantId), this::mapUsage);
        return found.isEmpty() ? null : found.getFirst();
    }

    private Usage mapUsage(ResultSet rs, int rowNum) throws SQLException {
        return new Usage(
                rs.getString("grant_id"),
                rs.getBigDecimal("consumed"),
                rs.getTimestamp("period_start").toInstant(),
                rs.getTimestamp("period_end").toInstant());
    }

    private static MapSqlParameterSource params(String tenantId, String grantId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("grantId", grantId);
    }
}
