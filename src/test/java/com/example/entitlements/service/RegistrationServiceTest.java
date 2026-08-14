package com.example.entitlements.service;

import com.example.entitlements.domain.*;
import com.example.entitlements.request.*;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationServiceTest {
    private TenantRegistry registry;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
        service = new RegistrationService(
                registry,
                new EntitlementHistoryService(registry, new EntitlementHistoryStore(), java.time.Clock.systemUTC()));
    }

    @Test
    void registersNestedScopeTreeAndSubjects() {
        Tenant tenant = service.register(TestFixtures.registration());

        assertEquals("root", tenant.getRootScopeId());
        assertEquals("engineering", tenant.getScopes().get("backend").getParentScopeId());
        assertEquals("backend", tenant.getSubjects().get("alice").getScopeId());
        assertTrue(tenant.getScopes().get("engineering").getChildScopeIds().contains("ml"));
        assertEquals(5, tenant.getScopes().size());
        assertEquals(4, tenant.getSubjects().size());
    }

    @Test
    void registersResourcesDefinitionsPropertiesAndInitialGrants() {
        Tenant tenant = service.register(TestFixtures.registration());

        assertEquals(2, tenant.getResources().size());
        assertNotNull(tenant.getResources().get("api").definition("api.requests"));
        assertInstanceOf(QuotaValue.class, tenant.getResources().get("api").properties().get("totalCapacity"));
        assertEquals(10, tenant.getGrants().size());
    }

    @Test
    void rejectsDuplicateTenantRegistration() {
        service.register(TestFixtures.registration());
        assertThrows(IllegalArgumentException.class, () -> service.register(TestFixtures.registration()));
    }

    @Test
    void rejectsDuplicateScopeIds() {
        ScopeInput root = new ScopeInput("root", "company", "Root", Map.of(),
                List.of(new ScopeInput("root", "team", "Duplicate", Map.of(), List.of(), List.of())), List.of());
        RegistrationRequest request = new RegistrationRequest(new TenantInput("t", "T"), root, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void rejectsDuplicateSubjectIdsAcrossScopes() {
        SubjectInput shared = new SubjectInput("same", "employee", "Same", Map.of());
        ScopeInput root = new ScopeInput("root", "company", "Root", Map.of(), List.of(
                new ScopeInput("a", "team", "A", Map.of(), List.of(), List.of(shared)),
                new ScopeInput("b", "team", "B", Map.of(), List.of(), List.of(shared))
        ), List.of());
        RegistrationRequest request = new RegistrationRequest(new TenantInput("t", "T"), root, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void rejectsGrantForUnknownScope() {
        RegistrationRequest base = TestFixtures.registration();
        GrantInput bad = new GrantInput("bad", new Target(TargetType.SCOPE, "missing"), "api", "api.enabled", new BooleanValue(true));
        RegistrationRequest request = new RegistrationRequest(base.tenant(), base.structure(), base.resources(), List.of(bad));

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void rejectsGrantForUnknownResource() {
        RegistrationRequest base = TestFixtures.registration();
        GrantInput bad = new GrantInput("bad", new Target(TargetType.SCOPE, "root"), "missing", "api.enabled", new BooleanValue(true));
        RegistrationRequest request = new RegistrationRequest(base.tenant(), base.structure(), base.resources(), List.of(bad));

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void rejectsGrantForUnknownEntitlementDefinition() {
        RegistrationRequest base = TestFixtures.registration();
        GrantInput bad = new GrantInput("bad", new Target(TargetType.SCOPE, "root"), "api", "missing", new BooleanValue(true));
        RegistrationRequest request = new RegistrationRequest(base.tenant(), base.structure(), base.resources(), List.of(bad));

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void rejectsGrantWhoseValueTypeDoesNotMatchDefinition() {
        RegistrationRequest base = TestFixtures.registration();
        GrantInput bad = new GrantInput("bad", new Target(TargetType.SCOPE, "root"), "api", "api.enabled",
                new QuantityValue(BigDecimal.ONE, "unit"));
        RegistrationRequest request = new RegistrationRequest(base.tenant(), base.structure(), base.resources(), List.of(bad));

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void rejectsDuplicateDefinitionKeysOnAResource() {
        Resource badResource = new Resource("r", "service", "R", Map.of(), Map.of(), List.of(
                new EntitlementDefinition("same", "A", EntitlementValueType.BOOLEAN),
                new EntitlementDefinition("same", "B", EntitlementValueType.TEXT)
        ));
        RegistrationRequest request = new RegistrationRequest(
                new TenantInput("t", "T"),
                new ScopeInput("root", "root", "Root", Map.of(), List.of(), List.of()),
                List.of(badResource), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }
}
