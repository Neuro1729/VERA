package com.example.entitlements.service;

import com.example.entitlements.cache.ResolutionKey;
import com.example.entitlements.domain.BooleanValue;
import com.example.entitlements.domain.EntitlementChangeType;
import com.example.entitlements.domain.EntitlementDefinition;
import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.EntitlementHistoryEvent;
import com.example.entitlements.domain.EntitlementValueType;
import com.example.entitlements.domain.QuantityValue;
import com.example.entitlements.domain.QuotaPeriod;
import com.example.entitlements.domain.QuotaValue;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.BulkSyncPreview;
import com.example.entitlements.request.BulkSyncRequest;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.request.ConsumptionRequest;
import com.example.entitlements.request.GrantInput;
import com.example.entitlements.request.GrantsSyncInput;
import com.example.entitlements.request.OrganizationSyncInput;
import com.example.entitlements.request.ResourcesSyncInput;
import com.example.entitlements.request.ScopeInput;
import com.example.entitlements.request.SubjectInput;
import com.example.entitlements.request.SyncMode;
import com.example.entitlements.testutil.EntitlementEngineHarness;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BulkSyncServiceTest {
    private EntitlementEngineHarness engine;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        engine = EntitlementEngineHarness.create();
        tenant = engine.registration.register(TestFixtures.registration());
    }

    @Test
    void mergeAddsScopes() {
        ScopeInput structure = TestFixtures.addChildScope(
                TestFixtures.toScopeInput(tenant),
                "engineering",
                new ScopeInput("data", "team", "Data", Map.of(), List.of(), List.of()));
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.MERGE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().scopesAdded());
        assertEquals("engineering", tenant.getScopes().get("data").getParentScopeId());
        assertTrue(tenant.getScopes().containsKey("backend"));
    }

    @Test
    void mergeAddsSubjects() {
        ScopeInput structure = addSubject(
                TestFixtures.toScopeInput(tenant),
                "ml",
                new SubjectInput("dana", "contractor", "Dana", Map.of()));
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.MERGE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().subjectsAdded());
        assertEquals("ml", tenant.getSubjects().get("dana").getScopeId());
        assertTrue(tenant.getSubjects().containsKey("alice"));
    }

    @Test
    void mergeUpdatesSubject() {
        ScopeInput structure = renameSubject(TestFixtures.toScopeInput(tenant), "alice", "Alice A");
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.MERGE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().subjectsUpdated());
        assertEquals("Alice A", tenant.getSubjects().get("alice").getName());
    }

    @Test
    void mergeMovesSubject() {
        ScopeInput structure = moveSubject(TestFixtures.toScopeInput(tenant), "alice", "ml");
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.MERGE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().subjectsMoved());
        assertEquals("ml", tenant.getSubjects().get("alice").getScopeId());
    }

    @Test
    void mergeMovesScope() {
        ScopeInput structure = moveScope(TestFixtures.toScopeInput(tenant), "backend", "marketing");
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.MERGE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().scopesMoved());
        assertEquals("marketing", tenant.getScopes().get("backend").getParentScopeId());
        assertTrue(tenant.getScopes().get("engineering").getChildScopeIds().contains("ml"));
    }

    @Test
    void mergeLeavesAbsentEntitiesUntouched() {
        ScopeInput onlyRootAndNew = TestFixtures.addChildScope(
                new ScopeInput("root", "company", "Acme", Map.of(), List.of(), List.of()),
                "root",
                new ScopeInput("finance", "department", "Finance", Map.of(), List.of(), List.of()));
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.MERGE, onlyRootAndNew));
        assertTrue(preview.valid());
        assertTrue(tenant.getScopes().containsKey("engineering"));
        assertTrue(tenant.getSubjects().containsKey("alice"));
        assertTrue(tenant.getScopes().containsKey("finance"));
    }

    @Test
    void reconcileRemovesMissingSubject() {
        ScopeInput structure = TestFixtures.removeSubjectFromTree(TestFixtures.toScopeInput(tenant), "eve");
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.RECONCILE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().subjectsRemoved());
        assertFalse(tenant.getSubjects().containsKey("eve"));
        assertTrue(tenant.getSubjects().containsKey("alice"));
    }

    @Test
    void reconcileRemovesMissingScope() {
        ScopeInput structure = TestFixtures.removeScopeFromTree(TestFixtures.toScopeInput(tenant), "marketing");
        BulkSyncPreview preview = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.RECONCILE, structure));
        assertTrue(preview.valid());
        assertFalse(tenant.getScopes().containsKey("marketing"));
        assertFalse(tenant.getSubjects().containsKey("eve"));
        assertTrue(tenant.getScopes().containsKey("engineering"));
    }

    @Test
    void reconcileNeverRemovesRoot() {
        ScopeInput structure = new ScopeInput("engineering", "department", "Engineering", Map.of(), List.of(), List.of());
        BulkSyncPreview preview = engine.bulkSync.previewOrganization(
                "acme", new OrganizationSyncInput(SyncMode.RECONCILE, structure));
        assertFalse(preview.valid());
        assertTrue(tenant.getScopes().containsKey("root"));
        BulkSyncPreview applied = engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.RECONCILE, structure));
        assertFalse(applied.valid());
        assertTrue(tenant.getScopes().containsKey("root"));
    }

    @Test
    void removalPreviewReportsGrantsAutomaticallyRemoved() {
        engine.commands.execute(new CommandRequest(
                CommandType.SET_ENTITLEMENT, "acme", engine.mapper.valueToTree(Map.of(
                "grantId", "g-mkt",
                "target", Map.of("type", "SCOPE", "id", "marketing"),
                "resourceId", "api",
                "entitlementKey", "api.enabled",
                "value", Map.of("type", "BOOLEAN", "value", true)))));
        ScopeInput structure = TestFixtures.removeScopeFromTree(TestFixtures.toScopeInput(tenant), "marketing");
        BulkSyncPreview preview = engine.bulkSync.previewOrganization(
                "acme", new OrganizationSyncInput(SyncMode.RECONCILE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().scopesRemoved());
        assertEquals(1, preview.summary().grantsAutomaticallyRemoved());
        assertEquals(0, preview.summary().invalidGrantCount());
        assertEquals(1, preview.impactSummary().grantsAutomaticallyRemoved());
    }

    @Test
    void deletionPreservesUsageHistory() {
        engine.usage.consume(new ConsumptionRequest("acme", "eve", "api", "api.requests", BigDecimal.TEN));
        assertTrue(engine.usageHistoryStore.hasHistory("acme", "api"));
        ScopeInput structure = TestFixtures.removeScopeFromTree(TestFixtures.toScopeInput(tenant), "marketing");
        assertTrue(engine.bulkSync.applyOrganization(
                "acme", new OrganizationSyncInput(SyncMode.RECONCILE, structure)).valid());
        assertTrue(engine.usageHistoryStore.hasHistory("acme", "api"));
        assertFalse(engine.usageHistoryStore.findBucketsByResource("acme", "api").isEmpty());
    }

    @Test
    void mergeAddsResource() {
        Resource storage = new Resource("storage", "storage", "Object Storage", Map.of(), Map.of(), List.of());
        BulkSyncPreview preview = engine.bulkSync.applyResources(
                "acme", new ResourcesSyncInput(SyncMode.MERGE, List.of(storage)));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().resourcesAdded());
        assertTrue(tenant.getResources().containsKey("storage"));
        assertTrue(tenant.getResources().containsKey("api"));
    }

    @Test
    void mergeUpdatesResource() {
        Resource gpu = tenant.getResources().get("gpu");
        Resource updated = new Resource(
                gpu.id(), gpu.kind(), "GPU Cluster v2", gpu.metadata(),
                Map.of("capacity", new QuantityValue(new BigDecimal("150"), "gpu")),
                gpu.entitlementDefinitions());
        BulkSyncPreview preview = engine.bulkSync.applyResources(
                "acme", new ResourcesSyncInput(SyncMode.MERGE, List.of(updated)));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().resourcesUpdated());
        assertEquals("GPU Cluster v2", tenant.getResources().get("gpu").name());
        assertTrue(tenant.getResources().containsKey("api"));
    }

    @Test
    void mergeLeavesAbsentResourceUnchanged() {
        Resource storage = new Resource("storage", "storage", "Object Storage", Map.of(), Map.of(), List.of());
        engine.bulkSync.applyResources("acme", new ResourcesSyncInput(SyncMode.MERGE, List.of(storage)));
        assertEquals("AI API", tenant.getResources().get("api").name());
        assertEquals("GPU Cluster", tenant.getResources().get("gpu").name());
    }

    @Test
    void reconcileRemovesAbsentResource() {
        Resource api = tenant.getResources().get("api");
        BulkSyncPreview preview = engine.bulkSync.applyResources(
                "acme", new ResourcesSyncInput(SyncMode.RECONCILE, List.of(api)));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().resourcesRemoved());
        assertFalse(tenant.getResources().containsKey("gpu"));
        assertTrue(tenant.getResources().containsKey("api"));
    }

    @Test
    void resourceRemovalReportsDependentGrants() {
        addGpuHoursGrant();
        Resource api = tenant.getResources().get("api");
        BulkSyncPreview preview = engine.bulkSync.previewResources(
                "acme", new ResourcesSyncInput(SyncMode.RECONCILE, List.of(api)));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().resourcesRemoved());
        assertEquals(1, preview.summary().grantsAutomaticallyRemoved());
        assertEquals(0, preview.summary().invalidGrantCount());
    }

    @Test
    void usageHistorySurvivesResourceRemoval() {
        addGpuHoursGrant();
        engine.usage.consume(new ConsumptionRequest("acme", "alice", "gpu", "gpu.hours", BigDecimal.ONE));
        assertTrue(engine.usageHistoryStore.hasHistory("acme", "gpu"));
        Resource api = tenant.getResources().get("api");
        assertTrue(engine.bulkSync.applyResources(
                "acme", new ResourcesSyncInput(SyncMode.RECONCILE, List.of(api))).valid());
        assertTrue(engine.usageHistoryStore.hasHistory("acme", "gpu"));
    }

    @Test
    void reconcileReplacesResourceMetadataAndPropertiesExactly() {
        Resource gpu = tenant.getResources().get("gpu");
        Resource desired = new Resource(
                gpu.id(), gpu.kind(), gpu.name(), Map.of("tier", "gold"), Map.of(), gpu.entitlementDefinitions());
        assertTrue(engine.bulkSync.applyResources(
                "acme", new ResourcesSyncInput(SyncMode.RECONCILE, List.of(
                        tenant.getResources().get("api"), desired))).valid());
        assertEquals(Map.of("tier", "gold"), tenant.getResources().get("gpu").metadata());
        assertTrue(tenant.getResources().get("gpu").properties().isEmpty());
    }

    @Test
    void removingDefinitionWhileSurvivingGrantUsesItBlocksApply() {
        addGpuHoursGrant();
        Resource gpu = tenant.getResources().get("gpu");
        Resource stripped = new Resource(
                gpu.id(), gpu.kind(), gpu.name(), gpu.metadata(), gpu.properties(),
                List.of(new EntitlementDefinition("gpu.enabled", "GPU Enabled", EntitlementValueType.BOOLEAN)));
        BulkSyncPreview preview = engine.bulkSync.previewResources(
                "acme", new ResourcesSyncInput(SyncMode.RECONCILE, List.of(
                        tenant.getResources().get("api"), stripped)));
        assertFalse(preview.valid());
        assertEquals(1, preview.summary().invalidGrantCount());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                "GRANT_ENTITLEMENT_DEFINITION_MISSING".equals(issue.code())));
        BulkSyncPreview applied = engine.bulkSync.applyResources(
                "acme", new ResourcesSyncInput(SyncMode.RECONCILE, List.of(
                        tenant.getResources().get("api"), stripped)));
        assertFalse(applied.valid());
        assertNotNull(tenant.getResources().get("gpu").definition("gpu.hours"));
        assertTrue(tenant.getGrants().containsKey("g-eng-hours"));
    }

    @Test
    void mergeCreatesGrant() {
        GrantInput grant = new GrantInput(
                "g-gpu-on", new Target(TargetType.SCOPE, "engineering"), "gpu", "gpu.enabled", new BooleanValue(true));
        BulkSyncPreview preview = engine.bulkSync.applyGrants(
                "acme", new GrantsSyncInput(SyncMode.MERGE, List.of(grant)));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().grantsCreated());
        assertTrue(tenant.getGrants().containsKey("g-gpu-on"));
        assertEquals(11, tenant.getGrants().size());
    }

    @Test
    void mergeUpdatesLogicalGrant() {
        GrantInput updated = new GrantInput(
                "g-eng-quota", new Target(TargetType.SCOPE, "engineering"), "api", "api.requests",
                new QuotaValue(new BigDecimal("42"), "request", QuotaPeriod.MONTHLY));
        BulkSyncPreview preview = engine.bulkSync.applyGrants(
                "acme", new GrantsSyncInput(SyncMode.MERGE, List.of(updated)));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().grantsUpdated());
        QuotaValue value = (QuotaValue) tenant.getGrants().get("g-eng-quota").value();
        assertEquals(0, value.limit().compareTo(new BigDecimal("42")));
        List<EntitlementHistoryEvent> events = engine.historyStore.findByResource("acme", "api");
        assertEquals(1, events.stream().filter(event -> event.changeType() == EntitlementChangeType.UPDATED).count());
    }

    @Test
    void mergeNoOpWhenLogicalGrantUnchanged() {
        BulkSyncPreview preview = engine.bulkSync.applyGrants(
                "acme", new GrantsSyncInput(SyncMode.MERGE, TestFixtures.grantInputs(tenant)));
        assertTrue(preview.valid());
        assertEquals(0, preview.summary().grantsCreated());
        assertEquals(0, preview.summary().grantsUpdated());
        assertEquals(0, preview.summary().grantsRemoved());
    }

    @Test
    void mergeLeavesAbsentGrantsUntouched() {
        GrantInput extra = new GrantInput(
                "g-gpu-on", new Target(TargetType.SCOPE, "root"), "gpu", "gpu.enabled", new BooleanValue(true));
        engine.bulkSync.applyGrants("acme", new GrantsSyncInput(SyncMode.MERGE, List.of(extra)));
        assertTrue(tenant.getGrants().containsKey("g-eng-quota"));
        assertEquals(11, tenant.getGrants().size());
    }

    @Test
    void reconcileRemovesMissingGrant() {
        List<GrantInput> desired = TestFixtures.grantInputs(tenant).stream()
                .filter(grant -> !"g-eng-quota".equals(grant.id()))
                .toList();
        BulkSyncPreview preview = engine.bulkSync.applyGrants(
                "acme", new GrantsSyncInput(SyncMode.RECONCILE, desired));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().grantsRemoved());
        assertFalse(tenant.getGrants().containsKey("g-eng-quota"));
        assertTrue(engine.historyStore.findByResource("acme", "api").stream()
                .anyMatch(event -> event.changeType() == EntitlementChangeType.REMOVED
                        && "g-eng-quota".equals(event.previousGrantId())));
    }

    @Test
    void blankGrantIdPreservesExistingId() {
        GrantInput updated = new GrantInput(
                "", new Target(TargetType.SCOPE, "engineering"), "api", "api.requests",
                new QuotaValue(new BigDecimal("7"), "request", QuotaPeriod.MONTHLY));
        assertTrue(engine.bulkSync.applyGrants(
                "acme", new GrantsSyncInput(SyncMode.MERGE, List.of(updated))).valid());
        assertTrue(tenant.getGrants().containsKey("g-eng-quota"));
        QuotaValue value = (QuotaValue) tenant.getGrants().get("g-eng-quota").value();
        assertEquals(0, value.limit().compareTo(new BigDecimal("7")));
    }

    @Test
    void newBlankGrantIdGeneratesUuid() {
        GrantInput created = new GrantInput(
                null, new Target(TargetType.SCOPE, "root"), "gpu", "gpu.enabled", new BooleanValue(true));
        assertTrue(engine.bulkSync.applyGrants(
                "acme", new GrantsSyncInput(SyncMode.MERGE, List.of(created))).valid());
        EntitlementGrant grant = tenant.findGrant(
                new Target(TargetType.SCOPE, "root"), "gpu", "gpu.enabled").orElseThrow();
        assertDoesNotThrow(() -> UUID.fromString(grant.id()));
    }

    @Test
    void organizationPlusResourcesSucceeds() {
        ScopeInput structure = TestFixtures.addChildScope(
                TestFixtures.toScopeInput(tenant), "root",
                new ScopeInput("finance", "department", "Finance", Map.of(), List.of(), List.of()));
        Resource storage = new Resource("storage", "storage", "Object Storage", Map.of(), Map.of(), List.of());
        BulkSyncPreview preview = engine.bulkSync.apply("acme", new BulkSyncRequest(
                new OrganizationSyncInput(SyncMode.MERGE, structure),
                new ResourcesSyncInput(SyncMode.MERGE, List.of(storage)),
                null));
        assertTrue(preview.valid());
        assertTrue(tenant.getScopes().containsKey("finance"));
        assertTrue(tenant.getResources().containsKey("storage"));
    }

    @Test
    void organizationPlusGrantsSucceeds() {
        ScopeInput structure = TestFixtures.addChildScope(
                TestFixtures.toScopeInput(tenant), "engineering",
                new ScopeInput("data", "team", "Data", Map.of(), List.of(), List.of()));
        GrantInput grant = new GrantInput(
                "g-data", new Target(TargetType.SCOPE, "data"), "api", "api.enabled", new BooleanValue(true));
        BulkSyncPreview preview = engine.bulkSync.apply("acme", new BulkSyncRequest(
                new OrganizationSyncInput(SyncMode.MERGE, structure),
                null,
                new GrantsSyncInput(SyncMode.MERGE, List.of(grant))));
        assertTrue(preview.valid());
        assertTrue(tenant.getScopes().containsKey("data"));
        assertTrue(tenant.getGrants().containsKey("g-data"));
    }

    @Test
    void resourcesPlusGrantsSucceeds() {
        Resource storage = new Resource(
                "storage", "storage", "Object Storage", Map.of(), Map.of(),
                List.of(new EntitlementDefinition("storage.enabled", "Enabled", EntitlementValueType.BOOLEAN)));
        GrantInput grant = new GrantInput(
                "g-store", new Target(TargetType.SCOPE, "root"), "storage", "storage.enabled", new BooleanValue(true));
        BulkSyncPreview preview = engine.bulkSync.apply("acme", new BulkSyncRequest(
                null,
                new ResourcesSyncInput(SyncMode.MERGE, List.of(storage)),
                new GrantsSyncInput(SyncMode.MERGE, List.of(grant))));
        assertTrue(preview.valid());
        assertTrue(tenant.getResources().containsKey("storage"));
        assertTrue(tenant.getGrants().containsKey("g-store"));
    }

    @Test
    void allThreeDomainsSucceed() {
        ScopeInput structure = TestFixtures.addChildScope(
                TestFixtures.toScopeInput(tenant), "root",
                new ScopeInput("finance", "department", "Finance", Map.of(), List.of(), List.of()));
        Resource storage = new Resource(
                "storage", "storage", "Object Storage", Map.of(), Map.of(),
                List.of(new EntitlementDefinition("storage.enabled", "Enabled", EntitlementValueType.BOOLEAN)));
        GrantInput grant = new GrantInput(
                "g-fin", new Target(TargetType.SCOPE, "finance"), "storage", "storage.enabled", new BooleanValue(true));
        BulkSyncPreview preview = engine.bulkSync.apply("acme", new BulkSyncRequest(
                new OrganizationSyncInput(SyncMode.MERGE, structure),
                new ResourcesSyncInput(SyncMode.MERGE, List.of(storage)),
                new GrantsSyncInput(SyncMode.MERGE, List.of(grant))));
        assertTrue(preview.valid());
        assertTrue(tenant.getScopes().containsKey("finance"));
        assertTrue(tenant.getResources().containsKey("storage"));
        assertTrue(tenant.getGrants().containsKey("g-fin"));
    }

    @Test
    void combinedResourceDefinitionRemovalWithGrantFixSucceeds() {
        addGpuHoursGrant();
        Resource gpu = tenant.getResources().get("gpu");
        Resource stripped = new Resource(
                gpu.id(), gpu.kind(), gpu.name(), gpu.metadata(), gpu.properties(),
                List.of(new EntitlementDefinition("gpu.enabled", "GPU Enabled", EntitlementValueType.BOOLEAN)));
        List<GrantInput> grants = TestFixtures.grantInputs(tenant).stream()
                .filter(grant -> !"g-eng-hours".equals(grant.id()))
                .toList();
        BulkSyncPreview preview = engine.bulkSync.preview("acme", new BulkSyncRequest(
                null,
                new ResourcesSyncInput(SyncMode.RECONCILE, List.of(tenant.getResources().get("api"), stripped)),
                new GrantsSyncInput(SyncMode.RECONCILE, grants)));
        assertTrue(preview.valid());
        assertEquals(0, preview.summary().invalidGrantCount());
        assertTrue(engine.bulkSync.apply("acme", new BulkSyncRequest(
                null,
                new ResourcesSyncInput(SyncMode.RECONCILE, List.of(tenant.getResources().get("api"), stripped)),
                new GrantsSyncInput(SyncMode.RECONCILE, grants))).valid());
        assertNull(tenant.getResources().get("gpu").definition("gpu.hours"));
        assertFalse(tenant.getGrants().containsKey("g-eng-hours"));
    }

    @Test
    void sameResourceChangeWithoutGrantChangeIsBlocked() {
        addGpuHoursGrant();
        Resource gpu = tenant.getResources().get("gpu");
        Resource stripped = new Resource(
                gpu.id(), gpu.kind(), gpu.name(), gpu.metadata(), gpu.properties(),
                List.of(new EntitlementDefinition("gpu.enabled", "GPU Enabled", EntitlementValueType.BOOLEAN)));
        BulkSyncPreview preview = engine.bulkSync.apply("acme", new BulkSyncRequest(
                null,
                new ResourcesSyncInput(SyncMode.RECONCILE, List.of(tenant.getResources().get("api"), stripped)),
                null));
        assertFalse(preview.valid());
        assertTrue(preview.summary().invalidGrantCount() > 0);
        assertNotNull(tenant.getResources().get("gpu").definition("gpu.hours"));
    }

    @Test
    void newScopePlusGrantTargetingThatScopeSucceeds() {
        organizationPlusGrantsSucceeds();
    }

    @Test
    void newResourcePlusGrantTargetingThatResourceSucceeds() {
        resourcesPlusGrantsSucceeds();
    }

    @Test
    void removeSubjectPlusExplicitGrantReconcileIsDeterministic() {
        engine.commands.execute(new CommandRequest(
                CommandType.SET_ENTITLEMENT, "acme", engine.mapper.valueToTree(Map.of(
                "grantId", "g-alice",
                "target", Map.of("type", "SUBJECT", "id", "alice"),
                "resourceId", "api",
                "entitlementKey", "api.enabled",
                "value", Map.of("type", "BOOLEAN", "value", true)))));
        ScopeInput structure = TestFixtures.removeSubjectFromTree(TestFixtures.toScopeInput(tenant), "alice");
        List<GrantInput> grants = TestFixtures.grantInputs(tenant).stream()
                .filter(grant -> !"g-alice".equals(grant.id()))
                .toList();
        BulkSyncPreview preview = engine.bulkSync.apply("acme", new BulkSyncRequest(
                new OrganizationSyncInput(SyncMode.RECONCILE, structure),
                null,
                new GrantsSyncInput(SyncMode.RECONCILE, grants)));
        assertTrue(preview.valid());
        assertFalse(tenant.getSubjects().containsKey("alice"));
        assertFalse(tenant.getGrants().containsKey("g-alice"));
        assertEquals(1, preview.summary().grantsAutomaticallyRemoved());
        assertEquals(0, preview.summary().grantsRemoved());
    }

    @Test
    void projectedTenantDoesNotMutateCachedTenant() {
        int scopeCount = tenant.getScopes().size();
        String aliceScope = tenant.getSubjects().get("alice").getScopeId();
        ScopeInput structure = TestFixtures.addChildScope(
                TestFixtures.toScopeInput(tenant), "engineering",
                new ScopeInput("data", "team", "Data", Map.of(), List.of(), List.of()));
        BulkSyncPreview preview = engine.bulkSync.previewOrganization(
                "acme", new OrganizationSyncInput(SyncMode.MERGE, structure));
        assertTrue(preview.valid());
        assertEquals(1, preview.summary().scopesAdded());
        assertEquals(scopeCount, tenant.getScopes().size());
        assertFalse(tenant.getScopes().containsKey("data"));
        assertEquals(aliceScope, tenant.getSubjects().get("alice").getScopeId());
    }

    @Test
    void countsAreDeterministic() {
        ScopeInput structure = TestFixtures.addChildScope(
                TestFixtures.toScopeInput(tenant), "engineering",
                new ScopeInput("data", "team", "Data", Map.of(), List.of(), List.of()));
        OrganizationSyncInput input = new OrganizationSyncInput(SyncMode.MERGE, structure);
        BulkSyncPreview first = engine.bulkSync.previewOrganization("acme", input);
        BulkSyncPreview second = engine.bulkSync.previewOrganization("acme", input);
        assertEquals(first.summary(), second.summary());
        assertEquals(first.changes().size(), second.changes().size());
    }

    @Test
    void warningAndErrorCountsAreCorrect() {
        addGpuHoursGrant();
        Resource gpu = tenant.getResources().get("gpu");
        Resource stripped = new Resource(
                gpu.id(), gpu.kind(), gpu.name(), gpu.metadata(), gpu.properties(),
                List.of(new EntitlementDefinition("gpu.enabled", "GPU Enabled", EntitlementValueType.BOOLEAN)));
        BulkSyncPreview preview = engine.bulkSync.previewResources(
                "acme", new ResourcesSyncInput(SyncMode.RECONCILE, List.of(
                        tenant.getResources().get("api"), stripped)));
        assertTrue(preview.summary().errorCount() > 0);
        assertEquals(preview.summary().errorCount(),
                preview.issues().stream().filter(issue -> issue.severity().name().equals("ERROR")).count());
        assertEquals(0, preview.summary().warningCount());
    }

    @Test
    void bulkSyncCannotReturnStaleNearestWinsResolution() {
        engine.commands.execute(new CommandRequest(
                CommandType.SET_ENTITLEMENT, "acme", engine.mapper.valueToTree(Map.of(
                "grantId", "g-eng-hours",
                "target", Map.of("type", "SCOPE", "id", "engineering"),
                "resourceId", "gpu",
                "entitlementKey", "gpu.hours",
                "value", Map.of("type", "QUOTA", "limit", 5000, "unit", "hour", "period", "MONTHLY")))));
        EntitlementResolver resolver = new EntitlementResolver(engine.cache);
        assertEquals("g-eng-hours", resolver.resolve(tenant, "alice", "gpu", "gpu.hours").orElseThrow().grant().id());
        GrantInput updated = new GrantInput(
                "g-backend-hours", new Target(TargetType.SCOPE, "backend"), "gpu", "gpu.hours",
                new QuotaValue(new BigDecimal("9"), "hour", QuotaPeriod.MONTHLY));
        assertTrue(engine.bulkSync.applyGrants(
                "acme", new GrantsSyncInput(SyncMode.MERGE, List.of(updated))).valid());
        assertTrue(engine.cache.get(new ResolutionKey("acme", "alice", "gpu", "gpu.hours")).isEmpty());
        assertEquals("g-backend-hours", resolver.resolve(tenant, "alice", "gpu", "gpu.hours").orElseThrow().grant().id());
    }

    private void addGpuHoursGrant() {
        engine.commands.execute(new CommandRequest(
                CommandType.SET_ENTITLEMENT, "acme", engine.mapper.valueToTree(Map.of(
                "grantId", "g-eng-hours",
                "target", Map.of("type", "SCOPE", "id", "engineering"),
                "resourceId", "gpu",
                "entitlementKey", "gpu.hours",
                "value", Map.of("type", "QUOTA", "limit", 5000, "unit", "hour", "period", "MONTHLY")))));
    }

    private static ScopeInput addSubject(ScopeInput node, String scopeId, SubjectInput subject) {
        if (node.id().equals(scopeId)) {
            List<SubjectInput> subjects = new ArrayList<>(node.subjects() == null ? List.of() : node.subjects());
            subjects.add(subject);
            return new ScopeInput(node.id(), node.kind(), node.name(), node.metadata(), node.children(), subjects);
        }
        List<ScopeInput> children = (node.children() == null ? List.<ScopeInput>of() : node.children()).stream()
                .map(child -> addSubject(child, scopeId, subject))
                .toList();
        return new ScopeInput(node.id(), node.kind(), node.name(), node.metadata(), children, node.subjects());
    }

    private static ScopeInput renameSubject(ScopeInput node, String subjectId, String name) {
        List<SubjectInput> subjects = (node.subjects() == null ? List.<SubjectInput>of() : node.subjects()).stream()
                .map(subject -> subject.id().equals(subjectId)
                        ? new SubjectInput(subject.id(), subject.kind(), name, subject.metadata())
                        : subject)
                .toList();
        List<ScopeInput> children = (node.children() == null ? List.<ScopeInput>of() : node.children()).stream()
                .map(child -> renameSubject(child, subjectId, name))
                .toList();
        return new ScopeInput(node.id(), node.kind(), node.name(), node.metadata(), children, subjects);
    }

    private static ScopeInput moveSubject(ScopeInput node, String subjectId, String newScopeId) {
        ScopeInput removed = TestFixtures.removeSubjectFromTree(node, subjectId);
        return addSubject(removed, newScopeId, new SubjectInput(subjectId, "employee", "Alice", Map.of()));
    }

    private static ScopeInput moveScope(ScopeInput node, String scopeId, String newParentId) {
        ScopeInput extracted = findScope(node, scopeId);
        ScopeInput without = TestFixtures.removeScopeFromTree(node, scopeId);
        return TestFixtures.addChildScope(without, newParentId, extracted);
    }

    private static ScopeInput findScope(ScopeInput node, String scopeId) {
        if (node.id().equals(scopeId)) return node;
        for (ScopeInput child : node.children() == null ? List.<ScopeInput>of() : node.children()) {
            ScopeInput found = findScope(child, scopeId);
            if (found != null) return found;
        }
        return null;
    }
}
