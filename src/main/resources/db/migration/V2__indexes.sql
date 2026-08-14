CREATE INDEX idx_scopes_parent ON scopes (tenant_id, parent_scope_id);
CREATE INDEX idx_subjects_scope ON subjects (tenant_id, scope_id);
CREATE INDEX idx_entitlement_grants_resource ON entitlement_grants (tenant_id, resource_id);
CREATE INDEX idx_entitlement_history_resource_changed
    ON entitlement_history (tenant_id, resource_id, changed_at);
CREATE INDEX idx_usage_events_resource_occurred
    ON usage_events (tenant_id, resource_id, occurred_at);
CREATE INDEX idx_usage_buckets_resource_start
    ON usage_buckets (tenant_id, resource_id, bucket_start);
