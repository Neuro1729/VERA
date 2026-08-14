package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.domain.*;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.request.GrantInput;
import com.example.entitlements.request.RegistrationRequest;
import com.example.entitlements.request.TenantInput;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.MutableClock;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class EntitlementHistoryServiceTest {
    private TenantRegistry registry;
    private EntitlementHistoryStore historyStore;
    private EntitlementHistoryService historyService;
    private MutableClock clock;
    private RegistrationService registrationService;
    private CommandService commandService;
    private ObjectMapper mapper;
    private Instant createdAt;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
        historyStore = new EntitlementHistoryStore();
        createdAt = Instant.parse("2026-08-01T10:00:00Z");
        clock = new MutableClock(createdAt);
        historyService = new EntitlementHistoryService(registry, historyStore, clock);
        registrationService = new RegistrationService(registry, historyService);
        mapper = new ObjectMapper().findAndRegisterModules();
        GrantResolutionCache cache = new GrantResolutionCache();
        EntitlementResolver resolver = new EntitlementResolver(cache);
        commandService = new CommandService(
                registry,
                new UsageStore(),
                mapper,
                cache,
                new ResolutionCacheInvalidator(cache),
                new RateLimitService(registry, resolver, clock),
                historyService);
        registrationService.register(TestFixtures.registration());
    }

    @Test
    void registrationCreatesCreatedHistoryForInitialGrants() {
        List<EntitlementHistoryEvent> quota = changes("api", "api.requests");
        assertEquals(2, quota.size());
        assertTrue(quota.stream().allMatch(event -> event.changeType() == EntitlementChangeType.CREATED));
        assertTrue(quota.stream().allMatch(event -> event.changedAt().equals(createdAt)));
        assertTrue(quota.stream().allMatch(event -> event.oldValue() == null && event.previousGrantId() == null));

        EntitlementHistoryEvent engineering = quota.stream()
                .filter(event -> "g-eng-quota".equals(event.newGrantId()))
                .findFirst()
                .orElseThrow();
        assertEquals(new Target(TargetType.SCOPE, "engineering"), engineering.target());
        assertEquals(new QuotaValue(new BigDecimal("1000000"), "request", QuotaPeriod.MONTHLY), engineering.newValue());
    }

    @Test
    void resourceHistoryReturnsEveryCurrentEntitlementKeyInOneQuery() {
        ResourceEntitlementHistory history = historyService.getHistory("acme", "gpu");
        assertEquals("gpu", history.resourceId());
        assertEquals("GPU Cluster", history.resourceName());
        assertEquals(List.of("gpu.enabled", "gpu.hours"), history.entitlements().stream()
                .map(ResourceEntitlementHistory.EntitlementTimeline::entitlementKey)
                .toList());
        assertTrue(history.entitlements().stream()
                .allMatch(timeline -> timeline.changes().isEmpty()));
    }

    @Test
    void entitlementDefinitionWithNoEventsReturnsEmptyChanges() {
        ResourceEntitlementHistory.EntitlementTimeline enabled = timeline("gpu", "gpu.enabled");
        assertEquals("GPU Enabled", enabled.entitlementName());
        assertEquals(EntitlementValueType.BOOLEAN, enabled.valueType());
        assertTrue(enabled.changes().isEmpty());
    }

    @Test
    void setEntitlementNewAssignmentCreatesHistory() throws Exception {
        clock.setInstant(Instant.parse("2026-08-10T14:30:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);

        List<EntitlementHistoryEvent> changes = changes("gpu", "gpu.hours");
        assertEquals(1, changes.size());
        EntitlementHistoryEvent created = changes.getFirst();
        assertEquals(EntitlementChangeType.CREATED, created.changeType());
        assertNull(created.previousGrantId());
        assertEquals("g-eng-hours", created.newGrantId());
        assertNull(created.oldValue());
        assertEquals(new QuotaValue(new BigDecimal("5000"), "hour", QuotaPeriod.MONTHLY), created.newValue());
        assertEquals(new Target(TargetType.SCOPE, "engineering"), created.target());
        assertEquals(Instant.parse("2026-08-10T14:30:00Z"), created.changedAt());
    }

    @Test
    void setEntitlementSameGrantIdChangedValueIsUpdated() throws Exception {
        clock.setInstant(Instant.parse("2026-08-10T14:30:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-quota","target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":8000,"unit":"request","period":"MONTHLY"}}
                """);

        List<EntitlementHistoryEvent> changes = changes("api", "api.requests");
        EntitlementHistoryEvent updated = last(changes);
        assertEquals(EntitlementChangeType.UPDATED, updated.changeType());
        assertEquals("g-eng-quota", updated.previousGrantId());
        assertEquals("g-eng-quota", updated.newGrantId());
        assertEquals(new QuotaValue(new BigDecimal("1000000"), "request", QuotaPeriod.MONTHLY), updated.oldValue());
        assertEquals(new QuotaValue(new BigDecimal("8000"), "request", QuotaPeriod.MONTHLY), updated.newValue());
        assertEquals(new Target(TargetType.SCOPE, "engineering"), updated.target());
        assertEquals(Instant.parse("2026-08-10T14:30:00Z"), updated.changedAt());
        assertEquals(1, changes.stream().filter(event -> event.changeType() == EntitlementChangeType.UPDATED).count());
        assertEquals(0, changes.stream().filter(event -> event.changeType() == EntitlementChangeType.REMOVED).count());
    }

    @Test
    void setEntitlementReplacementRecordsOneUpdatedEventNotRemovedPlusCreated() throws Exception {
        clock.setInstant(Instant.parse("2026-08-10T14:30:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"replacement","target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":2500000,"unit":"request","period":"MONTHLY"}}
                """);

        List<EntitlementHistoryEvent> changes = changes("api", "api.requests");
        EntitlementHistoryEvent updated = last(changes);
        assertEquals(EntitlementChangeType.UPDATED, updated.changeType());
        assertEquals("g-eng-quota", updated.previousGrantId());
        assertEquals("replacement", updated.newGrantId());
        assertEquals(new QuotaValue(new BigDecimal("1000000"), "request", QuotaPeriod.MONTHLY), updated.oldValue());
        assertEquals(new QuotaValue(new BigDecimal("2500000"), "request", QuotaPeriod.MONTHLY), updated.newValue());
        assertEquals(0, changes.stream().filter(event -> event.changeType() == EntitlementChangeType.REMOVED).count());
        assertEquals(2, changes.stream().filter(event -> event.changeType() == EntitlementChangeType.CREATED).count());
        assertEquals(1, changes.stream().filter(event -> event.changeType() == EntitlementChangeType.UPDATED).count());
    }

    @Test
    void removeEntitlementRecordsRemovedHistory() throws Exception {
        clock.setInstant(Instant.parse("2026-08-25T09:00:00Z"));
        execute("REMOVE_ENTITLEMENT", """
                {"target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.requests"}
                """);

        EntitlementHistoryEvent removed = last(changes("api", "api.requests"));
        assertEquals(EntitlementChangeType.REMOVED, removed.changeType());
        assertEquals("g-eng-quota", removed.previousGrantId());
        assertNull(removed.newGrantId());
        assertEquals(new QuotaValue(new BigDecimal("1000000"), "request", QuotaPeriod.MONTHLY), removed.oldValue());
        assertNull(removed.newValue());
        assertEquals(new Target(TargetType.SCOPE, "engineering"), removed.target());
        assertEquals(Instant.parse("2026-08-25T09:00:00Z"), removed.changedAt());
    }

    @Test
    void deletingGrantDoesNotErasePreviousCreatedOrUpdatedHistory() throws Exception {
        clock.setInstant(Instant.parse("2026-08-10T14:30:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-quota","target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":8000,"unit":"request","period":"MONTHLY"}}
                """);
        clock.setInstant(Instant.parse("2026-08-25T09:00:00Z"));
        execute("REMOVE_ENTITLEMENT", """
                {"target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.requests"}
                """);

        List<EntitlementChangeType> types = changes("api", "api.requests").stream()
                .filter(event -> "engineering".equals(event.target().id()))
                .map(EntitlementHistoryEvent::changeType)
                .toList();
        assertEquals(List.of(
                EntitlementChangeType.CREATED,
                EntitlementChangeType.UPDATED,
                EntitlementChangeType.REMOVED), types);
    }

    @Test
    void failedSetEntitlementCreatesNoHistory() {
        int before = historyStore.findByResource("acme", "api").size();
        assertThrows(IllegalArgumentException.class, () -> execute("SET_ENTITLEMENT", """
                {"grantId":"bad","target":{"type":"SCOPE","id":"missing"},"resourceId":"api","entitlementKey":"api.requests","value":{"type":"QUOTA","limit":1,"unit":"request","period":"MONTHLY"}}
                """));
        assertThrows(IllegalArgumentException.class, () -> execute("SET_ENTITLEMENT", """
                {"grantId":"bad","target":{"type":"SCOPE","id":"engineering"},"resourceId":"api","entitlementKey":"api.enabled","value":{"type":"QUOTA","limit":1,"unit":"request","period":"MONTHLY"}}
                """));
        assertEquals(before, historyStore.findByResource("acme", "api").size());
    }

    @Test
    void failedRemoveEntitlementCreatesNoHistory() {
        int before = historyStore.findByResource("acme", "gpu").size();
        assertThrows(NoSuchElementException.class, () -> execute("REMOVE_ENTITLEMENT", """
                {"target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours"}
                """));
        assertEquals(before, historyStore.findByResource("acme", "gpu").size());
    }

    @Test
    void failedRegistrationCreatesNoHistory() {
        historyStore.clear();
        registry.clear();
        RegistrationRequest base = TestFixtures.registration();
        RegistrationRequest invalid = new RegistrationRequest(
                base.tenant(),
                base.structure(),
                base.resources(),
                List.of(new GrantInput(
                        "bad",
                        new Target(TargetType.SCOPE, "missing"),
                        "api",
                        "api.enabled",
                        new BooleanValue(true))));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(invalid));
        assertTrue(historyStore.findByResource("acme", "api").isEmpty());
    }

    @Test
    void noOpSetEntitlementDoesNotAppendHistory() throws Exception {
        clock.setInstant(Instant.parse("2026-08-10T14:30:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-backend-hours","target":{"type":"SCOPE","id":"backend"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);
        int afterCreate = changes("gpu", "gpu.hours").size();
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-backend-hours","target":{"type":"SCOPE","id":"backend"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);
        assertEquals(afterCreate, changes("gpu", "gpu.hours").size());
    }

    @Test
    void removeSubjectRecordsRemovedHistoryForDirectGrantsOnly() throws Exception {
        execute("SET_ENTITLEMENT", """
                {"grantId":"alice-enabled","target":{"type":"SUBJECT","id":"alice"},"resourceId":"api","entitlementKey":"api.enabled","value":{"type":"BOOLEAN","value":true}}
                """);
        int engineeringEnabled = (int) changes("api", "api.enabled").stream()
                .filter(event -> "engineering".equals(event.target().id()))
                .count();

        clock.setInstant(Instant.parse("2026-08-20T12:00:00Z"));
        execute("REMOVE_SUBJECT", "{\"subjectId\":\"alice\"}");

        List<EntitlementHistoryEvent> aliceRemoved = changes("api", "api.enabled").stream()
                .filter(event -> event.changeType() == EntitlementChangeType.REMOVED)
                .filter(event -> "alice".equals(event.target().id()))
                .toList();
        assertEquals(1, aliceRemoved.size());
        assertEquals("alice-enabled", aliceRemoved.getFirst().previousGrantId());
        assertEquals(new Target(TargetType.SUBJECT, "alice"), aliceRemoved.getFirst().target());
        assertEquals(engineeringEnabled, changes("api", "api.enabled").stream()
                .filter(event -> "engineering".equals(event.target().id()))
                .count());
    }

    @Test
    void removeScopeRecordsRemovedHistoryForScopeAndSubtreeTargets() throws Exception {
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-backend-hours","target":{"type":"SCOPE","id":"backend"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":8000,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"alice-hours","target":{"type":"SUBJECT","id":"alice"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":100,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"charlie-hours","target":{"type":"SUBJECT","id":"charlie"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":50,"unit":"hour","period":"MONTHLY"}}
                """);

        clock.setInstant(Instant.parse("2026-08-21T09:00:00Z"));
        execute("REMOVE_SCOPE", "{\"scopeId\":\"backend\"}");

        List<EntitlementHistoryEvent> hours = changes("gpu", "gpu.hours");
        assertTrue(hours.stream().anyMatch(event ->
                event.changeType() == EntitlementChangeType.REMOVED && "g-backend-hours".equals(event.previousGrantId())
                        && event.target().equals(new Target(TargetType.SCOPE, "backend"))));
        assertTrue(hours.stream().anyMatch(event ->
                event.changeType() == EntitlementChangeType.REMOVED && "alice-hours".equals(event.previousGrantId())
                        && event.target().equals(new Target(TargetType.SUBJECT, "alice"))));
        assertTrue(hours.stream().noneMatch(event ->
                event.changeType() == EntitlementChangeType.REMOVED && "g-eng-hours".equals(event.previousGrantId())));
        assertTrue(hours.stream().noneMatch(event ->
                event.changeType() == EntitlementChangeType.REMOVED && "charlie-hours".equals(event.previousGrantId())));
        assertEquals(new Target(TargetType.SCOPE, "backend"),
                hours.stream().filter(event -> "g-backend-hours".equals(event.previousGrantId())
                        && event.changeType() == EntitlementChangeType.REMOVED).findFirst().orElseThrow().target());
    }

    @Test
    void removeScopeSubtreeRecordsRemovedHistoryForDescendantScopesAndSubjects() throws Exception {
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-backend-hours","target":{"type":"SCOPE","id":"backend"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":8000,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"alice-hours","target":{"type":"SUBJECT","id":"alice"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":100,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"charlie-hours","target":{"type":"SUBJECT","id":"charlie"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":50,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-mkt-hours","target":{"type":"SCOPE","id":"marketing"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":10,"unit":"hour","period":"MONTHLY"}}
                """);

        execute("REMOVE_SCOPE", "{\"scopeId\":\"engineering\"}");

        List<String> removedGrantIds = changes("gpu", "gpu.hours").stream()
                .filter(event -> event.changeType() == EntitlementChangeType.REMOVED)
                .map(EntitlementHistoryEvent::previousGrantId)
                .toList();
        assertTrue(removedGrantIds.containsAll(List.of("g-backend-hours", "alice-hours", "g-eng-hours", "charlie-hours")));
        assertFalse(removedGrantIds.contains("g-mkt-hours"));
    }

    @Test
    void deletingScopeDoesNotErasePreviousHistoryAndRetainsTargetIds() throws Exception {
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-backend-hours","target":{"type":"SCOPE","id":"backend"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":8000,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("REMOVE_SCOPE", "{\"scopeId\":\"backend\"}");

        List<EntitlementHistoryEvent> hours = changes("gpu", "gpu.hours");
        assertEquals(EntitlementChangeType.CREATED, hours.getFirst().changeType());
        assertEquals("backend", hours.getFirst().target().id());
        assertEquals("backend", last(hours).target().id());
        assertFalse(registry.getRequired("acme").getScopes().containsKey("backend"));
    }

    @Test
    void removeResourceRecordsRemovedHistoryButKeepsStoredEvents() throws Exception {
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);
        execute("REMOVE_RESOURCE", "{\"resourceId\":\"gpu\"}");

        List<EntitlementHistoryEvent> stored = historyStore.findByResource("acme", "gpu");
        assertTrue(stored.stream().anyMatch(event ->
                event.changeType() == EntitlementChangeType.REMOVED && "g-eng-hours".equals(event.previousGrantId())));
        assertThrows(NoSuchElementException.class, () -> historyService.getHistory("acme", "gpu"));
    }

    @Test
    void twoTenantsWithSameResourceIdHaveIsolatedHistory() {
        RegistrationRequest base = TestFixtures.registration();
        registrationService.register(new RegistrationRequest(
                new TenantInput("globex", "Globex"),
                base.structure(),
                base.resources(),
                base.grants()));

        List<EntitlementHistoryEvent> acme = historyStore.findByResource("acme", "api");
        List<EntitlementHistoryEvent> globex = historyStore.findByResource("globex", "api");
        assertFalse(acme.isEmpty());
        assertEquals(acme.size(), globex.size());
        assertTrue(acme.stream().allMatch(event -> "acme".equals(event.tenantId())));
        assertTrue(globex.stream().allMatch(event -> "globex".equals(event.tenantId())));
    }

    @Test
    void twoResourcesInSameTenantHaveIsolatedHistory() throws Exception {
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);
        assertTrue(historyStore.findByResource("acme", "gpu").stream()
                .allMatch(event -> "gpu".equals(event.resourceId())));
        assertTrue(historyStore.findByResource("acme", "api").stream()
                .allMatch(event -> "api".equals(event.resourceId())));
        assertTrue(changes("api", "api.requests").stream()
                .noneMatch(event -> "gpu.hours".equals(event.entitlementKey())));
    }

    @Test
    void bulkQueryGroupsByEntitlementKeyInChronologicalAppendOrder() throws Exception {
        clock.setInstant(Instant.parse("2026-08-10T14:30:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":5000,"unit":"hour","period":"MONTHLY"}}
                """);
        clock.setInstant(Instant.parse("2026-08-15T09:00:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-enabled","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.enabled","value":{"type":"BOOLEAN","value":true}}
                """);
        clock.setInstant(Instant.parse("2026-08-20T11:00:00Z"));
        execute("SET_ENTITLEMENT", """
                {"grantId":"g-eng-hours","target":{"type":"SCOPE","id":"engineering"},"resourceId":"gpu","entitlementKey":"gpu.hours","value":{"type":"QUOTA","limit":8000,"unit":"hour","period":"MONTHLY"}}
                """);

        ResourceEntitlementHistory history = historyService.getHistory("acme", "gpu");
        List<EntitlementHistoryEvent> hours = timeline("gpu", "gpu.hours").changes();
        List<EntitlementHistoryEvent> enabled = timeline("gpu", "gpu.enabled").changes();
        assertEquals(2, hours.size());
        assertEquals(List.of(EntitlementChangeType.CREATED, EntitlementChangeType.UPDATED),
                hours.stream().map(EntitlementHistoryEvent::changeType).toList());
        assertTrue(hours.getFirst().changedAt().isBefore(hours.get(1).changedAt()));
        assertEquals(1, enabled.size());
        assertEquals(history.entitlements().size(), 2);
    }

    @Test
    void missingTenantAndResourceThrowNotFound() {
        assertThrows(NoSuchElementException.class, () -> historyService.getHistory("missing", "gpu"));
        assertThrows(NoSuchElementException.class, () -> historyService.getHistory("acme", "missing"));
    }

    @Test
    void entitlementValuesSerializeWithExistingJacksonTypeProperty() throws Exception {
        JsonNode root = mapper.valueToTree(historyService.getHistory("acme", "api"));
        assertEquals("BOOLEAN", valueType(root, "api.enabled"));
        assertEquals("QUOTA", valueType(root, "api.requests"));
        assertEquals("RATE_LIMIT", valueType(root, "api.rateLimit"));
        assertEquals("QUANTITY", valueType(root, "api.maxBatch"));
        assertEquals("RANGE", valueType(root, "api.temperature"));
        assertEquals("SET", valueType(root, "api.models"));
        assertEquals("TEXT", valueType(root, "api.tier"));
        assertEquals("TIME_RANGE", valueType(root, "api.accessWindow"));
        assertTrue(root.at(timelinePath("api.models") + "/changes/0/newValue/values").isArray());
        assertEquals("premium", root.at(timelinePath("api.tier") + "/changes/0/newValue/value").asText());
        assertEquals("MONTHLY", root.at(timelinePath("api.requests") + "/changes/0/newValue/period").asText());
    }

    private void execute(String type, String payload) throws Exception {
        JsonNode node = mapper.readTree(payload);
        commandService.execute(new CommandRequest(CommandType.valueOf(type), "acme", node));
    }

    private ResourceEntitlementHistory.EntitlementTimeline timeline(String resourceId, String key) {
        return historyService.getHistory("acme", resourceId).entitlements().stream()
                .filter(timeline -> timeline.entitlementKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private List<EntitlementHistoryEvent> changes(String resourceId, String key) {
        return timeline(resourceId, key).changes();
    }

    private static EntitlementHistoryEvent last(List<EntitlementHistoryEvent> events) {
        return events.get(events.size() - 1);
    }

    private static String timelinePath(String key) {
        return "/entitlements/" + switch (key) {
            case "api.enabled" -> 0;
            case "api.requests" -> 1;
            case "api.rateLimit" -> 2;
            case "api.maxBatch" -> 3;
            case "api.temperature" -> 4;
            case "api.models" -> 5;
            case "api.tier" -> 6;
            case "api.accessWindow" -> 7;
            default -> throw new IllegalArgumentException(key);
        };
    }

    private static String valueType(JsonNode root, String key) {
        return root.at(timelinePath(key) + "/changes/0/newValue/type").asText();
    }
}
