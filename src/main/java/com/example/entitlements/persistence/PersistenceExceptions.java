package com.example.entitlements.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

public final class PersistenceExceptions {
    private PersistenceExceptions() {}

    public static RuntimeException translate(DataAccessException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
        if (ex instanceof DuplicateKeyException || detail.contains("duplicate key")) {
            if (detail.contains("entitlement_grants_logical_key")) {
                return new IllegalStateException("grant already exists for target/resource/entitlement");
            }
            if (detail.contains("tenants_pkey")) {
                return new IllegalArgumentException("tenant already exists");
            }
            if (detail.contains("scopes_pkey")) {
                return new IllegalArgumentException("scope already exists");
            }
            if (detail.contains("subjects_pkey")) {
                return new IllegalArgumentException("subject already exists");
            }
            if (detail.contains("resources_pkey")) {
                return new IllegalArgumentException("resource already exists");
            }
            return new IllegalArgumentException("duplicate key");
        }
        if (ex instanceof DataIntegrityViolationException) {
            return new IllegalArgumentException("invalid reference");
        }
        return ex;
    }
}
