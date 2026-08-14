package com.example.entitlements.persistence.postgres;

import com.example.entitlements.domain.TenantAdmin;
import com.example.entitlements.persistence.PersistenceExceptions;
import com.example.entitlements.persistence.TenantAdminRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class PostgresTenantAdminRepository implements TenantAdminRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresTenantAdminRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(TenantAdmin admin) {
        try {
            jdbc.update(
                    """
                    INSERT INTO tenant_admin (id, tenant_id, email, normalized_email, password_hash, created_at)
                    VALUES (:id, :tenantId, :email, :normalizedEmail, :passwordHash, :createdAt)
                    """,
                    params()
                            .addValue("id", admin.id())
                            .addValue("tenantId", admin.tenantId())
                            .addValue("email", admin.email())
                            .addValue("normalizedEmail", admin.normalizedEmail())
                            .addValue("passwordHash", admin.passwordHash())
                            .addValue("createdAt", Timestamp.from(admin.createdAt())));
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public Optional<TenantAdmin> findByNormalizedEmail(String normalizedEmail) {
        return queryOne("SELECT * FROM tenant_admin WHERE normalized_email = :normalizedEmail",
                params().addValue("normalizedEmail", normalizedEmail));
    }

    @Override
    public Optional<TenantAdmin> findByTenantId(String tenantId) {
        return queryOne("SELECT * FROM tenant_admin WHERE tenant_id = :tenantId",
                params().addValue("tenantId", tenantId));
    }

    @Override
    public boolean existsByTenantId(String tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_admin WHERE tenant_id = :tenantId",
                params().addValue("tenantId", tenantId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_admin WHERE normalized_email = :normalizedEmail",
                params().addValue("normalizedEmail", normalizedEmail),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void clear() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE tenant_admin");
    }

    private Optional<TenantAdmin> queryOne(String sql, MapSqlParameterSource params) {
        List<TenantAdmin> rows = jdbc.query(sql, params, (rs, rowNum) -> map(rs));
        return rows.stream().findFirst();
    }

    private static TenantAdmin map(ResultSet rs) throws SQLException {
        return new TenantAdmin(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("email"),
                rs.getString("normalized_email"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }
}
