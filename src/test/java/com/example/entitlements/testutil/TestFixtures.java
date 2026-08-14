package com.example.entitlements.testutil;

import com.example.entitlements.domain.*;
import com.example.entitlements.request.*;
import com.example.entitlements.service.EntitlementHistoryService;
import com.example.entitlements.service.RegistrationService;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TestFixtures {
    private TestFixtures() {}

    public static RegistrationRequest registration() {
        ScopeInput structure = new ScopeInput(
                "root", "company", "Acme", Map.of(),
                List.of(
                        new ScopeInput("engineering", "department", "Engineering", Map.of(),
                                List.of(
                                        new ScopeInput("backend", "team", "Backend", Map.of(), List.of(),
                                                List.of(
                                                        new SubjectInput("alice", "employee", "Alice", Map.of()),
                                                        new SubjectInput("bob", "employee", "Bob", Map.of())
                                                )),
                                        new ScopeInput("ml", "team", "ML", Map.of(), List.of(),
                                                List.of(new SubjectInput("charlie", "researcher", "Charlie", Map.of())))
                                ), List.of()),
                        new ScopeInput("marketing", "department", "Marketing", Map.of(), List.of(),
                                List.of(new SubjectInput("eve", "employee", "Eve", Map.of())))
                ),
                List.of()
        );

        Resource api = new Resource(
                "api", "api", "AI API", Map.of("provider", "example"),
                Map.of("totalCapacity", new QuotaValue(new BigDecimal("10000000"), "request", QuotaPeriod.MONTHLY)),
                List.of(
                        new EntitlementDefinition("api.enabled", "API Enabled", EntitlementValueType.BOOLEAN),
                        new EntitlementDefinition("api.requests", "API Requests", EntitlementValueType.QUOTA),
                        new EntitlementDefinition("api.rateLimit", "API Rate Limit", EntitlementValueType.RATE_LIMIT),
                        new EntitlementDefinition("api.maxBatch", "Max Batch", EntitlementValueType.QUANTITY),
                        new EntitlementDefinition("api.temperature", "Temperature", EntitlementValueType.RANGE),
                        new EntitlementDefinition("api.models", "Allowed Models", EntitlementValueType.SET),
                        new EntitlementDefinition("api.tier", "Tier", EntitlementValueType.TEXT),
                        new EntitlementDefinition("api.accessWindow", "Access Window", EntitlementValueType.TIME_RANGE)
                ));

        Resource gpu = new Resource(
                "gpu", "compute", "GPU Cluster", Map.of(),
                Map.of("capacity", new QuantityValue(new BigDecimal("100"), "gpu")),
                List.of(
                        new EntitlementDefinition("gpu.enabled", "GPU Enabled", EntitlementValueType.BOOLEAN),
                        new EntitlementDefinition("gpu.hours", "GPU Hours", EntitlementValueType.QUOTA)
                ));

        return new RegistrationRequest(
                new TenantInput("acme", "Acme Corp"),
                structure,
                List.of(api, gpu),
                List.of(
                        new GrantInput("g-root-enabled", new Target(TargetType.SCOPE, "root"), "api", "api.enabled", new BooleanValue(true)),
                        new GrantInput("g-root-quota", new Target(TargetType.SCOPE, "root"), "api", "api.requests", new QuotaValue(new BigDecimal("100000"), "request", QuotaPeriod.MONTHLY)),
                        new GrantInput("g-root-rate", new Target(TargetType.SCOPE, "root"), "api", "api.rateLimit",
                                new RateLimitValue(new BigDecimal("20"), new BigDecimal("20"), Duration.ofMinutes(1))),
                        new GrantInput("g-eng-quota", new Target(TargetType.SCOPE, "engineering"), "api", "api.requests", new QuotaValue(new BigDecimal("1000000"), "request", QuotaPeriod.MONTHLY)),
                        new GrantInput("g-eng-rate", new Target(TargetType.SCOPE, "engineering"), "api", "api.rateLimit",
                                new RateLimitValue(new BigDecimal("100"), new BigDecimal("100"), Duration.ofMinutes(1))),
                        new GrantInput("g-batch", new Target(TargetType.SCOPE, "engineering"), "api", "api.maxBatch", new QuantityValue(new BigDecimal("100"), "request")),
                        new GrantInput("g-range", new Target(TargetType.SCOPE, "engineering"), "api", "api.temperature", new RangeValue(new BigDecimal("0"), new BigDecimal("2"), "value")),
                        new GrantInput("g-models", new Target(TargetType.SCOPE, "engineering"), "api", "api.models", new SetValue(Set.of("small", "large"))),
                        new GrantInput("g-tier", new Target(TargetType.SCOPE, "engineering"), "api", "api.tier", new TextValue("premium")),
                        new GrantInput("g-window", new Target(TargetType.SCOPE, "engineering"), "api", "api.accessWindow", new TimeRangeValue(Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")))
                )
        );
    }

    public static CompanyRegistrationRequest companyRegistration() {
        RegistrationRequest request = registration();
        return new CompanyRegistrationRequest(
                new OrganizationConfigInput(request.tenant(), request.structure()),
                new ResourcesConfigInput(request.resources()),
                new GrantsConfigInput(request.grants()));
    }

    public static CompanyRegistrationRequest companyRegistration(String tenantId, String name) {
        RegistrationRequest request = registration();
        return new CompanyRegistrationRequest(
                new OrganizationConfigInput(new TenantInput(tenantId, name), request.structure()),
                new ResourcesConfigInput(request.resources()),
                new GrantsConfigInput(request.grants()));
    }

    public static CompanySignupRequest signup() {
        return signup("acme", "Acme Corp", "admin@acme.com", "a-long-password");
    }

    public static CompanySignupRequest signup(String tenantId, String name, String email, String password) {
        return new CompanySignupRequest(
                new AdminRegistrationInput(email, password),
                companyRegistration(tenantId, name));
    }

    public static ScopeInput toScopeInput(Tenant tenant) {
        return toScopeInput(tenant, tenant.getRootScopeId());
    }

    public static ScopeInput toScopeInput(Tenant tenant, String scopeId) {
        Scope scope = tenant.getScopes().get(scopeId);
        List<ScopeInput> children = scope.getChildScopeIds().stream()
                .map(childId -> toScopeInput(tenant, childId))
                .toList();
        List<SubjectInput> subjects = scope.getSubjectIds().stream()
                .map(subjectId -> {
                    Subject subject = tenant.getSubjects().get(subjectId);
                    return new SubjectInput(subject.getId(), subject.getKind(), subject.getName(), subject.getMetadata());
                })
                .toList();
        return new ScopeInput(scope.getId(), scope.getKind(), scope.getName(), scope.getMetadata(), children, subjects);
    }

    public static ScopeInput addChildScope(ScopeInput node, String parentId, ScopeInput child) {
        if (node.id().equals(parentId)) {
            List<ScopeInput> children = new java.util.ArrayList<>(safe(node.children()));
            children.add(child);
            return new ScopeInput(node.id(), node.kind(), node.name(), node.metadata(), children, node.subjects());
        }
        List<ScopeInput> children = safe(node.children()).stream()
                .map(existing -> addChildScope(existing, parentId, child))
                .toList();
        return new ScopeInput(node.id(), node.kind(), node.name(), node.metadata(), children, node.subjects());
    }

    public static ScopeInput removeScopeFromTree(ScopeInput node, String removeId) {
        List<ScopeInput> children = safe(node.children()).stream()
                .filter(child -> !child.id().equals(removeId))
                .map(child -> removeScopeFromTree(child, removeId))
                .toList();
        return new ScopeInput(node.id(), node.kind(), node.name(), node.metadata(), children, node.subjects());
    }

    public static ScopeInput removeSubjectFromTree(ScopeInput node, String subjectId) {
        List<SubjectInput> subjects = safe(node.subjects()).stream()
                .filter(subject -> !subject.id().equals(subjectId))
                .toList();
        List<ScopeInput> children = safe(node.children()).stream()
                .map(child -> removeSubjectFromTree(child, subjectId))
                .toList();
        return new ScopeInput(node.id(), node.kind(), node.name(), node.metadata(), children, subjects);
    }

    public static List<GrantInput> grantInputs(Tenant tenant) {
        return tenant.getGrants().values().stream()
                .map(grant -> new GrantInput(
                        grant.id(), grant.target(), grant.resourceId(), grant.entitlementKey(), grant.value()))
                .toList();
    }

    public static List<Resource> resources(Tenant tenant) {
        return List.copyOf(tenant.getResources().values());
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static Tenant registeredTenant(TenantRegistry registry) {
        EntitlementHistoryService historyService = new EntitlementHistoryService(
                registry, new EntitlementHistoryStore(), Clock.systemUTC());
        return new RegistrationService(registry, historyService).register(registration());
    }
}
