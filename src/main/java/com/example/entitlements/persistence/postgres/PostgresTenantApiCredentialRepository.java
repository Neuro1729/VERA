package com.example.entitlements.persistence.postgres;

import com.example.entitlements.domain.TenantApiCredential;
import com.example.entitlements.persistence.PersistenceExceptions;
import com.example.entitlements.persistence.TenantApiCredentialRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class PostgresTenantApiCredentialRepository implements TenantApiCredentialRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresTenantApiCredentialRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(TenantApiCredential credential) {
        try {
            jdbc.update(
                    """
                    INSERT INTO tenant_api_credential
                        (id, tenant_id, public_id, secret_hash, enabled, created_at, rotated_at)
                    VALUES
                        (:id, :tenantId, :publicId, :secretHash, :enabled, :createdAt, :rotatedAt)
                    """,
                    params(credential));
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public void replace(TenantApiCredential credential) {
        try {
            int updated = jdbc.update(
                    """
                    UPDATE tenant_api_credential
                    SET public_id = :publicId,
                        secret_hash = :secretHash,
                        enabled = :enabled,
                        rotated_at = :rotatedAt
                    WHERE id = :id AND tenant_id = :tenantId
                    """,
                    params(credential));
            if (updated == 0) {
                throw new java.util.NoSuchElementException("API credential not found");
            }
        } catch (DataAccessException ex) {
            throw PersistenceExceptions.translate(ex);
        }
    }

    @Override
    public Optional<TenantApiCredential> findByPublicId(String publicId) {
        return queryOne("SELECT * FROM tenant_api_credential WHERE public_id = :publicId",
                new MapSqlParameterSource("publicId", publicId));
    }

    @Override
    public Optional<TenantApiCredential> findByTenantId(String tenantId) {
        return queryOne("SELECT * FROM tenant_api_credential WHERE tenant_id = :tenantId",
                new MapSqlParameterSource("tenantId", tenantId));
    }

    @Override
    public void clear() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE tenant_api_credential");
    }

    private Optional<TenantApiCredential> queryOne(String sql, MapSqlParameterSource params) {
        List<TenantApiCredential> rows = jdbc.query(sql, params, (rs, rowNum) -> map(rs));
        return rows.stream().findFirst();
    }

    private static TenantApiCredential map(ResultSet rs) throws SQLException {
        Timestamp rotated = rs.getTimestamp("rotated_at");
        return new TenantApiCredential(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("public_id"),
                rs.getString("secret_hash"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rotated == null ? null : rotated.toInstant());
    }

    private static MapSqlParameterSource params(TenantApiCredential credential) {
        return new MapSqlParameterSource()
                .addValue("id", credential.id())
                .addValue("tenantId", credential.tenantId())
                .addValue("publicId", credential.publicId())
                .addValue("secretHash", credential.secretHash())
                .addValue("enabled", credential.enabled())
                .addValue("createdAt", Timestamp.from(credential.createdAt()))
                .addValue("rotatedAt", credential.rotatedAt() == null ? null : Timestamp.from(credential.rotatedAt()));
    }
}
