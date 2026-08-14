package com.example.entitlements.persistence;

import com.example.entitlements.domain.BooleanValue;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.BulkSyncRequest;
import com.example.entitlements.request.GrantInput;
import com.example.entitlements.request.GrantsSyncInput;
import com.example.entitlements.request.OrganizationSyncInput;
import com.example.entitlements.request.ScopeInput;
import com.example.entitlements.request.SyncMode;
import com.example.entitlements.service.BulkSyncService;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresBulkSyncIT extends PostgresIntegrationTest {
    @Autowired BulkSyncService bulkSyncService;

    @Test
    void bulkApplyFailureRollsBackTenantHistoryAndEvictsCache() {
        registerAcme();
        cache.put(new com.example.entitlements.cache.ResolutionKey("acme", "alice", "api", "api.requests"), "g-eng-quota");
        int historyBefore = entitlementHistoryRepository.findByResource("acme", "api").size();

        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_grants() RETURNS trigger AS $$
                BEGIN
                  RAISE EXCEPTION 'forced grant failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER trg_fail_grants BEFORE INSERT ON entitlement_grants FOR EACH ROW EXECUTE FUNCTION fail_grants()");
        try {
            ScopeInput structure = TestFixtures.addChildScope(
                    TestFixtures.toScopeInput(registry.getRequired("acme")),
                    "root",
                    new ScopeInput("finance", "department", "Finance", Map.of(), List.of(), List.of()));
            GrantInput grant = new GrantInput(
                    "g-fin", new Target(TargetType.SCOPE, "finance"), "api", "api.enabled", new BooleanValue(true));
            assertThrows(RuntimeException.class, () -> bulkSyncService.apply("acme", new BulkSyncRequest(
                    new OrganizationSyncInput(SyncMode.MERGE, structure),
                    null,
                    new GrantsSyncInput(SyncMode.MERGE, List.of(grant)))));

            assertFalse(registry.all().stream().anyMatch(tenant -> tenant.getScopes().containsKey("finance")));
            Tenant reloaded = reload("acme");
            assertFalse(reloaded.getScopes().containsKey("finance"));
            assertFalse(reloaded.getGrants().containsKey("g-fin"));
            assertEquals(historyBefore, entitlementHistoryRepository.findByResource("acme", "api").size());
            assertEquals(5, reloaded.getScopes().size());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_grants ON entitlement_grants");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_grants()");
        }
    }
}
