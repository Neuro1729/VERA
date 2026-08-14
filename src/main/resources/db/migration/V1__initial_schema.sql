CREATE TABLE tenants (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    root_scope_id TEXT
);

CREATE TABLE scopes (
    tenant_id TEXT NOT NULL REFERENCES tenants (id),
    id TEXT NOT NULL,
    kind TEXT NOT NULL,
    name TEXT NOT NULL,
    parent_scope_id TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_order BIGSERIAL NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, parent_scope_id)
        REFERENCES scopes (tenant_id, id)
        ON DELETE CASCADE
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE subjects (
    tenant_id TEXT NOT NULL REFERENCES tenants (id),
    id TEXT NOT NULL,
    kind TEXT NOT NULL,
    name TEXT NOT NULL,
    scope_id TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_order BIGSERIAL NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, scope_id)
        REFERENCES scopes (tenant_id, id)
);

CREATE TABLE resources (
    tenant_id TEXT NOT NULL REFERENCES tenants (id),
    id TEXT NOT NULL,
    kind TEXT NOT NULL,
    name TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_order BIGSERIAL NOT NULL,
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE entitlement_definitions (
    tenant_id TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    entitlement_key TEXT NOT NULL,
    name TEXT NOT NULL,
    value_type TEXT NOT NULL,
    position INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, resource_id, entitlement_key),
    FOREIGN KEY (tenant_id, resource_id)
        REFERENCES resources (tenant_id, id)
        ON DELETE CASCADE
);

CREATE TABLE entitlement_grants (
    tenant_id TEXT NOT NULL REFERENCES tenants (id),
    id TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    entitlement_key TEXT NOT NULL,
    value_type TEXT NOT NULL,
    value_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT entitlement_grants_logical_key UNIQUE (
        tenant_id, target_type, target_id, resource_id, entitlement_key
    ),
    FOREIGN KEY (tenant_id, resource_id)
        REFERENCES resources (tenant_id, id)
);

CREATE TABLE usage_current (
    tenant_id TEXT NOT NULL,
    grant_id TEXT NOT NULL,
    consumed NUMERIC NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, grant_id),
    FOREIGN KEY (tenant_id, grant_id)
        REFERENCES entitlement_grants (tenant_id, id)
        ON DELETE CASCADE
);

CREATE TABLE rate_limit_state (
    tenant_id TEXT NOT NULL,
    grant_id TEXT NOT NULL,
    available_tokens NUMERIC NOT NULL,
    last_refill_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, grant_id),
    FOREIGN KEY (tenant_id, grant_id)
        REFERENCES entitlement_grants (tenant_id, id)
        ON DELETE CASCADE
);

-- Historical tables are independent of current-state rows.
CREATE TABLE entitlement_history (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    entitlement_key TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    change_type TEXT NOT NULL,
    previous_grant_id TEXT,
    new_grant_id TEXT,
    old_value JSONB,
    new_value JSONB,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE usage_events (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    resource_name_at_time TEXT,
    resource_kind_at_time TEXT,
    entitlement_key TEXT NOT NULL,
    grant_id TEXT NOT NULL,
    grant_target_type TEXT NOT NULL,
    grant_target_id TEXT NOT NULL,
    grant_target_name_at_time TEXT,
    subject_id TEXT NOT NULL,
    subject_name_at_time TEXT,
    used_value JSONB,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE usage_buckets (
    tenant_id TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    subject_name_at_time TEXT,
    resource_id TEXT NOT NULL,
    resource_name_at_time TEXT,
    resource_kind_at_time TEXT,
    entitlement_key TEXT NOT NULL,
    grant_id TEXT NOT NULL,
    grant_target_type TEXT NOT NULL,
    grant_target_id TEXT NOT NULL,
    grant_target_name_at_time TEXT,
    bucket_start TIMESTAMPTZ NOT NULL,
    bucket_end TIMESTAMPTZ NOT NULL,
    total_consumed NUMERIC NOT NULL,
    operation_count BIGINT NOT NULL,
    first_occurred_at TIMESTAMPTZ NOT NULL,
    last_occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (
        tenant_id,
        subject_id,
        resource_id,
        entitlement_key,
        grant_id,
        bucket_start
    )
);
