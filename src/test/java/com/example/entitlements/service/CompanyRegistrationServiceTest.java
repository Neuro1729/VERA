package com.example.entitlements.service;

import com.example.entitlements.domain.BooleanValue;
import com.example.entitlements.domain.QuantityValue;
import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.CompanyRegistrationRequest;
import com.example.entitlements.request.GrantInput;
import com.example.entitlements.request.GrantsConfigInput;
import com.example.entitlements.request.OrganizationConfigInput;
import com.example.entitlements.request.RegistrationPreview;
import com.example.entitlements.request.RegistrationRequest;
import com.example.entitlements.request.ResourcesConfigInput;
import com.example.entitlements.testutil.EntitlementEngineHarness;
import com.example.entitlements.testutil.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanyRegistrationServiceTest {
    private EntitlementEngineHarness engine;

    @BeforeEach
    void setUp() {
        engine = EntitlementEngineHarness.create();
    }

    @Test
    void threePartRegistrationPreviewIsValid() {
        RegistrationPreview preview = engine.companyRegistration.preview(TestFixtures.companyRegistration());
        assertTrue(preview.valid());
        assertEquals(5, preview.summary().scopeCount());
        assertEquals(4, preview.summary().subjectCount());
        assertEquals(2, preview.summary().resourceCount());
        assertEquals(10, preview.summary().entitlementDefinitionCount());
        assertEquals(10, preview.summary().grantCount());
        assertEquals(0, preview.summary().invalidGrantCount());
        assertEquals(0, preview.summary().errorCount());
        assertTrue(preview.issues().isEmpty());
    }

    @Test
    void previewHasNoSideEffects() {
        engine.companyRegistration.preview(TestFixtures.companyRegistration());
        assertTrue(engine.registry.all().isEmpty());
        assertTrue(engine.historyStore.findByResource("acme", "api").isEmpty());
        assertFalse(engine.usageHistoryStore.hasHistory("acme", "api"));
        assertEquals(0, engine.cache.size());
    }

    @Test
    void registrationAtomicallyCreatesTenant() {
        Tenant tenant = engine.companyRegistration.register(TestFixtures.companyRegistration());
        assertEquals("acme", tenant.getId());
        assertEquals("root", tenant.getRootScopeId());
        assertEquals(10, tenant.getGrants().size());
        assertEquals("acme", engine.registry.getRequired("acme").getId());
        assertFalse(engine.historyStore.findByResource("acme", "api").isEmpty());
    }

    @Test
    void invalidGrantTargetBlocksRegistration() {
        CompanyRegistrationRequest request = withGrant(new GrantInput(
                "bad", new Target(TargetType.SCOPE, "missing"), "api", "api.enabled", new BooleanValue(true)));
        RegistrationPreview preview = engine.companyRegistration.preview(request);
        assertFalse(preview.valid());
        assertTrue(preview.summary().invalidGrantCount() > 0);
        assertThrows(IllegalArgumentException.class, () -> engine.companyRegistration.register(request));
        assertTrue(engine.registry.all().isEmpty());
    }

    @Test
    void invalidResourceReferenceBlocksRegistration() {
        CompanyRegistrationRequest request = withGrant(new GrantInput(
                "bad", new Target(TargetType.SCOPE, "root"), "missing", "api.enabled", new BooleanValue(true)));
        assertFalse(engine.companyRegistration.preview(request).valid());
        assertThrows(IllegalArgumentException.class, () -> engine.companyRegistration.register(request));
    }

    @Test
    void invalidEntitlementKeyBlocksRegistration() {
        CompanyRegistrationRequest request = withGrant(new GrantInput(
                "bad", new Target(TargetType.SCOPE, "root"), "api", "missing", new BooleanValue(true)));
        assertFalse(engine.companyRegistration.preview(request).valid());
        assertThrows(IllegalArgumentException.class, () -> engine.companyRegistration.register(request));
    }

    @Test
    void valueTypeMismatchBlocksRegistration() {
        CompanyRegistrationRequest request = withGrant(new GrantInput(
                "bad", new Target(TargetType.SCOPE, "root"), "api", "api.enabled",
                new QuantityValue(BigDecimal.ONE, "unit")));
        assertFalse(engine.companyRegistration.preview(request).valid());
        assertThrows(IllegalArgumentException.class, () -> engine.companyRegistration.register(request));
    }

    @Test
    void duplicateLogicalGrantBlocksRegistration() {
        GrantInput first = new GrantInput(
                "g1", new Target(TargetType.SCOPE, "root"), "api", "api.enabled", new BooleanValue(true));
        GrantInput duplicate = new GrantInput(
                "g2", new Target(TargetType.SCOPE, "root"), "api", "api.enabled", new BooleanValue(false));
        CompanyRegistrationRequest base = TestFixtures.companyRegistration();
        CompanyRegistrationRequest request = new CompanyRegistrationRequest(
                base.organization(), base.resources(), new GrantsConfigInput(List.of(first, duplicate)));
        assertFalse(engine.companyRegistration.preview(request).valid());
        assertThrows(IllegalArgumentException.class, () -> engine.companyRegistration.register(request));
    }

    @Test
    void assemblesExistingRegistrationRequest() {
        CompanyRegistrationRequest company = TestFixtures.companyRegistration();
        RegistrationRequest assembled = engine.companyRegistration.toRegistrationRequest(company);
        assertEquals(company.organization().tenant(), assembled.tenant());
        assertEquals(company.organization().structure(), assembled.structure());
        assertEquals(company.resources().resources(), assembled.resources());
        assertEquals(company.grants().grants(), assembled.grants());
    }

    private static CompanyRegistrationRequest withGrant(GrantInput grant) {
        CompanyRegistrationRequest base = TestFixtures.companyRegistration();
        return new CompanyRegistrationRequest(
                new OrganizationConfigInput(base.organization().tenant(), base.organization().structure()),
                new ResourcesConfigInput(base.resources().resources()),
                new GrantsConfigInput(List.of(grant)));
    }
}
