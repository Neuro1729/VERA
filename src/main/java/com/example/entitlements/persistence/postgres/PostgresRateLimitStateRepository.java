package com.example.entitlements.persistence.postgres;

import com.example.entitlements.domain.RateLimitState;
import com.example.entitlements.persistence.RateLimitStateRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class PostgresRateLimitStateRepository implements RateLimitStateRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresRateLimitStateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RateLimitState get(String tenantId, String grantId) {
        return queryOne(tenantId, grantId, false);
    }

    @Override
    public RateLimitState lock(String tenantId, String grantId) {
        return queryOne(tenantId, grantId, true);
    }

    @Override
    public void save(String tenantId, String grantId, RateLimitState state) {
        jdbc.update(
                """
                INSERT INTO rate_limit_state (tenant_id, grant_id, available_tokens, last_refill_at)
                VALUES (:tenantId, :grantId, :tokens, :lastRefillAt)
                ON CONFLICT (tenant_id, grant_id) DO UPDATE SET
                    available_tokens = EXCLUDED.available_tokens,
                    last_refill_at = EXCLUDED.last_refill_at
                """,
                params(tenantId, grantId)
                        .addValue("tokens", state.getAvailableTokens())
                        .addValue("lastRefillAt", Timestamp.from(state.getLastRefillTime())));
    }

    @Override
    public void insertIfAbsent(String tenantId, String grantId, RateLimitState state) {
        jdbc.update(
                """
                INSERT INTO rate_limit_state (tenant_id, grant_id, available_tokens, last_refill_at)
                VALUES (:tenantId, :grantId, :tokens, :lastRefillAt)
                ON CONFLICT (tenant_id, grant_id) DO NOTHING
                """,
                params(tenantId, grantId)
                        .addValue("tokens", state.getAvailableTokens())
                        .addValue("lastRefillAt", Timestamp.from(state.getLastRefillTime())));
    }

    @Override
    public void remove(String tenantId, String grantId) {
        jdbc.update(
                "DELETE FROM rate_limit_state WHERE tenant_id = :tenantId AND grant_id = :grantId",
                params(tenantId, grantId));
    }

    @Override
    public boolean exists(String tenantId, String grantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rate_limit_state WHERE tenant_id = :tenantId AND grant_id = :grantId",
                params(tenantId, grantId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public int count() {
        Integer count = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM rate_limit_state", Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public void clear() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE rate_limit_state");
    }

    private RateLimitState queryOne(String tenantId, String grantId, boolean forUpdate) {
        String sql = """
                SELECT available_tokens, last_refill_at
                FROM rate_limit_state
                WHERE tenant_id = :tenantId AND grant_id = :grantId
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<RateLimitState> found = jdbc.query(sql, params(tenantId, grantId), this::mapState);
        return found.isEmpty() ? null : found.getFirst();
    }

    private RateLimitState mapState(ResultSet rs, int rowNum) throws SQLException {
        return new RateLimitState(
                rs.getBigDecimal("available_tokens"),
                rs.getTimestamp("last_refill_at").toInstant());
    }

    private static MapSqlParameterSource params(String tenantId, String grantId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("grantId", grantId);
    }
}
