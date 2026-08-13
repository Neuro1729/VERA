package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.cache.ResolutionKey;
import com.example.entitlements.domain.*;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageStore;
import com.example.entitlements.testutil.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandServiceTest {
    private TenantRegistry registry;
    private UsageStore usageStore;
    private Tenant tenant;
    private CommandService service;
    private ObjectMapper mapper;
    private GrantResolutionCache cache;
    private EntitlementResolver resolver;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
        usageStore = new UsageStore();
        tenant = TestFixtures.registeredTenant(registry);
        mapper = new ObjectMapper().findAndRegisterModules();
        cache = new GrantResolutionCache();
        ResolutionCacheInvalidator invalidator = new ResolutionCacheInvalidator(cache);
        EntitlementResolver entitlementResolver = new EntitlementResolver(cache);
        RateLimitService rateLimitService = new RateLimitService(registry, entitlementResolver, java.time.Clock.systemUTC());
        service = new CommandService(registry, usageStore, mapper, cache, invalidator, rateLimitService);
        resolver = entitlementResolver;
    }

    @Test
    void addsScopeUnderExistingScope() throws Exception {
        execute("ADD_SCOPE", "{\"parentScopeId\":\"engineering\",\"scope\":{\"id\":\"data\",\"kind\":\"team\",\"name\":\"Data\"}}");
        assertEquals("engineering", tenant.getScopes().get("data").getParentScopeId());
        assertTrue(tenant.getScopes().get("engineering").getChildScopeIds().contains("data"));
    }

    @Test
    void updatesScopeWithoutChangingUnspecifiedFields() throws Exception {
        execute("UPDATE_SCOPE", "{\"scopeId\":\"ml\",\"name\":\"AI Research\",\"metadata\":{\"costCenter\":\"R1\"}}");
        assertEquals("AI Research", tenant.getScopes().get("ml").getName());
        assertEquals("team", tenant.getScopes().get("ml").getKind());
        assertEquals("R1", tenant.getScopes().get("ml").getMetadata().get("costCenter"));
    }

    @Test
    void movesScopeAndPreservesSubtree() throws Exception {
        execute("MOVE_SCOPE", "{\"scopeId\":\"backend\",\"newParentScopeId\":\"marketing\"}");
        assertEquals("marketing", tenant.getScopes().get("backend").getParentScopeId());
        assertEquals("backend", tenant.getSubjects().get("alice").getScopeId());
    }

    @Test
    void rejectsScopeMoveThatCreatesCycle() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> execute("MOVE_SCOPE", "{\"scopeId\":\"engineering\",\"newParentScopeId\":\"backend\"}"));
    }

    @Test
    void removesScopeSubtreeSubjectsAndTheirGrants() throws Exception {
        tenant.putGrant(new EntitlementGrant("personal", new Target(TargetType.SUBJECT, "alice"), "api", "api.enabled", new BooleanValue(true)));
        execute("REMOVE_SCOPE", "{\"scopeId\":\"backend\"}");
        assertFalse(tenant.getScopes().containsKey("backend"));
        assertFalse(tenant.getSubjects().containsKey("alice"));
        assertFalse(tenant.getSubjects().containsKey("bob"));
        assertFalse(tenant.getGrants().containsKey("personal"));
    }

    @Test
    void rootScopeCannotBeRemoved() {
        assertThrows(IllegalArgumentException.class, () -> execute("REMOVE_SCOPE", "{\"scopeId\":\"root\"}"));
    }

    @Test
    void addsUpdatesMovesAndRemovesSubject() throws Exception {
        execute("ADD_SUBJECT", "{\"scopeId\":\"ml\",\"subject\":{\"id\":\"dana\",\"kind\":\"contractor\",\"name\":\"Dana\"}}");
        execute("UPDATE_SUBJECT", "{\"subjectId\":\"dana\",\"kind\":\"employee\",\"name\":\"Dana D\",\"metadata\":{\"level\":3}}");
        execute("MOVE_SUBJECT", "{\"subjectId\":\"dana\",\"newScopeId\":\"backend\"}");
        assertEquals("employee", tenant.getSubjects().get("dana").getKind());
        assertEquals("backend", tenant.getSubjects().get("dana").getScopeId());
        execute("REMOVE_SUBJECT", "{\"subjectId\":\"dana\"}");
        assertFalse(tenant.getSubjects().containsKey("dana"));
    }

    @Test
    void movingSubjectImmediatelyChangesInheritedEntitlement() throws Exception {
        assertEquals("g-eng-quota", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
        execute("MOVE_SUBJECT", "{\"subjectId\":\"alice\",\"newScopeId\":\"marketing\"}");
        assertEquals("g-root-quota", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
    }

    @Test
    void addsAndRemovesResource() throws Exception {
        execute("ADD_RESOURCE", "{\"resource\":{\"id\":\"storage\",\"kind\":\"storage\",\"name\":\"Object Storage\",\"properties\":{},\"entitlementDefinitions\":[]}}");
        assertTrue(tenant.getResources().containsKey("storage"));
        execute("REMOVE_RESOURCE", "{\"resourceId\":\"storage\"}");
        assertFalse(tenant.getResources().containsKey("storage"));
    }

    @Test
    void updatesGenericResourcePropertyCapacity() throws Exception {
        execute("UPDATE_RESOURCE", "{\"resourceId\":\"gpu\",\"properties\":{\"capacity\":{\"type\":\"QUANTITY\",\"value\":150,\"unit\":\"gpu\"}}}");
        QuantityValue capacity = (QuantityValue) tenant.getResources().get("gpu").properties().get("capacity");
        assertEquals(0, capacity.value().compareTo(new BigDecimal("150")));
    }

    @Test
    void setEntitlementCreatesNearestOverride() throws Exception {
        execute("SET_ENTITLEMENT", "{\"grantId\":\"g-backend\",\"target\":{\"type\":\"SCOPE\",\"id\":\"backend\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\",\"value\":{\"type\":\"QUOTA\",\"limit\":2000000,\"unit\":\"request\",\"period\":\"MONTHLY\"}}");
        assertEquals("g-backend", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
    }

    @Test
    void setEntitlementReplacesSameTargetResourceAndKey() throws Exception {
        execute("SET_ENTITLEMENT", "{\"grantId\":\"replacement\",\"target\":{\"type\":\"SCOPE\",\"id\":\"engineering\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\",\"value\":{\"type\":\"QUOTA\",\"limit\":2500000,\"unit\":\"request\",\"period\":\"MONTHLY\"}}");
        assertFalse(tenant.getGrants().containsKey("g-eng-quota"));
        assertEquals("replacement", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
    }

    @Test
    void removeEntitlementFallsBackToParent() throws Exception {
        execute("SET_ENTITLEMENT", "{\"grantId\":\"backend-x\",\"target\":{\"type\":\"SCOPE\",\"id\":\"backend\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\",\"value\":{\"type\":\"QUOTA\",\"limit\":50,\"unit\":\"request\",\"period\":\"MONTHLY\"}}");
        execute("REMOVE_ENTITLEMENT", "{\"target\":{\"type\":\"SCOPE\",\"id\":\"backend\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\"}");
        assertEquals("g-eng-quota", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
    }

    @Test
    void removingResourceAlsoRemovesItsGrants() throws Exception {
        execute("REMOVE_RESOURCE", "{\"resourceId\":\"api\"}");
        assertFalse(tenant.getResources().containsKey("api"));
        assertTrue(tenant.getGrants().values().stream().noneMatch(g -> g.resourceId().equals("api")));
    }

    @Test
    void resourceUpdateCannotInvalidateExistingGrantDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> execute("UPDATE_RESOURCE", "{\"resourceId\":\"api\",\"entitlementDefinitions\":[]}"));
    }

    @Test
    void addingNearerScopeGrantInvalidatesOnlyThatResourceKeyForDescendants() throws Exception {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        resolver.resolve(tenant, "alice", "api", "api.maxBatch");
        resolver.resolve(tenant, "eve", "api", "api.requests");

        execute("SET_ENTITLEMENT", "{\"grantId\":\"g-backend\",\"target\":{\"type\":\"SCOPE\",\"id\":\"backend\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\",\"value\":{\"type\":\"QUOTA\",\"limit\":2000000,\"unit\":\"request\",\"period\":\"MONTHLY\"}}");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertEquals(Optional.of("g-batch"), cache.get(new ResolutionKey("acme", "alice", "api", "api.maxBatch")));
        assertEquals(Optional.of("g-root-quota"), cache.get(new ResolutionKey("acme", "eve", "api", "api.requests")));
        assertEquals("g-backend", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
    }

    @Test
    void removingNearerGrantCausesDescendantsToResolveToParentAgain() throws Exception {
        execute("SET_ENTITLEMENT", "{\"grantId\":\"backend-x\",\"target\":{\"type\":\"SCOPE\",\"id\":\"backend\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\",\"value\":{\"type\":\"QUOTA\",\"limit\":50,\"unit\":\"request\",\"period\":\"MONTHLY\"}}");
        assertEquals("backend-x", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());

        execute("REMOVE_ENTITLEMENT", "{\"target\":{\"type\":\"SCOPE\",\"id\":\"backend\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\"}");
        assertEquals("g-eng-quota", resolver.resolve(tenant, "alice", "api", "api.requests").orElseThrow().grant().id());
    }

    @Test
    void moveSubjectInvalidatesOnlyMovedSubjectCache() throws Exception {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        resolver.resolve(tenant, "bob", "api", "api.requests");

        execute("MOVE_SUBJECT", "{\"subjectId\":\"alice\",\"newScopeId\":\"marketing\"}");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertEquals(Optional.of("g-eng-quota"), cache.get(new ResolutionKey("acme", "bob", "api", "api.requests")));
    }

    @Test
    void moveScopeInvalidatesAllSubjectsInMovedSubtree() throws Exception {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        resolver.resolve(tenant, "bob", "api", "api.requests");
        resolver.resolve(tenant, "charlie", "api", "api.requests");

        execute("MOVE_SCOPE", "{\"scopeId\":\"backend\",\"newParentScopeId\":\"marketing\"}");

        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "bob", "api", "api.requests")).isEmpty());
        assertEquals(Optional.of("g-eng-quota"), cache.get(new ResolutionKey("acme", "charlie", "api", "api.requests")));
    }

    @Test
    void addingEmptyScopeDoesNotInvalidateUnrelatedCacheEntries() throws Exception {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        int size = cache.size();
        execute("ADD_SCOPE", "{\"parentScopeId\":\"engineering\",\"scope\":{\"id\":\"data\",\"kind\":\"team\",\"name\":\"Data\"}}");
        assertEquals(size, cache.size());
        assertEquals(Optional.of("g-eng-quota"), cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")));
    }

    @Test
    void updatingScopeNameDoesNotInvalidateEntitlementCache() throws Exception {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        execute("UPDATE_SCOPE", "{\"scopeId\":\"engineering\",\"name\":\"Eng Renamed\"}");
        assertEquals(Optional.of("g-eng-quota"), cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")));
    }

    @Test
    void removingResourceInvalidatesThatResourcesCachedResolutions() throws Exception {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        resolver.resolve(tenant, "alice", "api", "api.maxBatch");
        execute("REMOVE_RESOURCE", "{\"resourceId\":\"api\"}");
        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")).isEmpty());
        assertTrue(cache.get(new ResolutionKey("acme", "alice", "api", "api.maxBatch")).isEmpty());
    }

    @Test
    void invalidationSucceedsWhenAffectedSubjectHasNoCachedEntries() throws Exception {
        assertDoesNotThrow(() -> execute(
                "SET_ENTITLEMENT",
                "{\"grantId\":\"g-backend\",\"target\":{\"type\":\"SCOPE\",\"id\":\"backend\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\",\"value\":{\"type\":\"QUOTA\",\"limit\":2000000,\"unit\":\"request\",\"period\":\"MONTHLY\"}}"
        ));
        assertEquals(0, cache.size());
    }

    @Test
    void financeChangeDoesNotInvalidateEngineeringSubjects() throws Exception {
        resolver.resolve(tenant, "alice", "api", "api.requests");
        execute("SET_ENTITLEMENT", "{\"grantId\":\"g-mkt\",\"target\":{\"type\":\"SCOPE\",\"id\":\"marketing\"},\"resourceId\":\"api\",\"entitlementKey\":\"api.requests\",\"value\":{\"type\":\"QUOTA\",\"limit\":10,\"unit\":\"request\",\"period\":\"MONTHLY\"}}");
        assertEquals(Optional.of("g-eng-quota"), cache.get(new ResolutionKey("acme", "alice", "api", "api.requests")));
    }

    private void execute(String type, String payload) throws Exception {
        JsonNode node = mapper.readTree(payload);
        service.execute(new CommandRequest(CommandType.valueOf(type), "acme", node));
    }
}
