package com.example.entitlements.service;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.domain.*;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandResult;
import com.example.entitlements.request.command.CommandPayloads.*;
import com.example.entitlements.persistence.TenantRepository;
import com.example.entitlements.persistence.UsageRepository;
import com.example.entitlements.persistence.memory.InMemoryTenantRepository;
import com.example.entitlements.store.CacheEvictOnRollback;
import com.example.entitlements.store.TenantRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CommandService {
    private final TenantRegistry registry;
    private final UsageRepository usageStore;
    private final ObjectMapper objectMapper;
    private final GrantResolutionCache cache;
    private final ResolutionCacheInvalidator invalidator;
    private final RateLimitService rateLimitService;
    private final EntitlementHistoryService historyService;
    private final TenantRepository tenantRepository;

    public CommandService(
            TenantRegistry registry,
            UsageRepository usageStore,
            ObjectMapper objectMapper,
            GrantResolutionCache cache,
            ResolutionCacheInvalidator invalidator,
            RateLimitService rateLimitService,
            EntitlementHistoryService historyService
    ) {
        this(registry, usageStore, objectMapper, cache, invalidator, rateLimitService, historyService,
                new InMemoryTenantRepository());
    }

    @Autowired
    public CommandService(
            TenantRegistry registry,
            UsageRepository usageStore,
            ObjectMapper objectMapper,
            GrantResolutionCache cache,
            ResolutionCacheInvalidator invalidator,
            RateLimitService rateLimitService,
            EntitlementHistoryService historyService,
            TenantRepository tenantRepository
    ) {
        this.registry = registry;
        this.usageStore = usageStore;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.invalidator = invalidator;
        this.rateLimitService = rateLimitService;
        this.historyService = historyService;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public CommandResult execute(CommandRequest request) {
        if (request == null || request.type() == null) throw new IllegalArgumentException("command type is required");
        Tenant tenant = registry.getRequired(request.tenantId());
        CacheEvictOnRollback.register(registry, tenant.getId());
        synchronized (tenant) {
            return switch (request.type()) {
                case ADD_SCOPE -> addScope(tenant, convert(request, AddScope.class));
                case UPDATE_SCOPE -> updateScope(tenant, convert(request, UpdateScope.class));
                case REMOVE_SCOPE -> removeScope(tenant, convert(request, RemoveScope.class));
                case MOVE_SCOPE -> moveScope(tenant, convert(request, MoveScope.class));
                case ADD_SUBJECT -> addSubject(tenant, convert(request, AddSubject.class));
                case UPDATE_SUBJECT -> updateSubject(tenant, convert(request, UpdateSubject.class));
                case REMOVE_SUBJECT -> removeSubject(tenant, convert(request, RemoveSubject.class));
                case MOVE_SUBJECT -> moveSubject(tenant, convert(request, MoveSubject.class));
                case ADD_RESOURCE -> addResource(tenant, convert(request, AddResource.class));
                case UPDATE_RESOURCE -> updateResource(tenant, convert(request, UpdateResource.class));
                case REMOVE_RESOURCE -> removeResource(tenant, convert(request, RemoveResource.class));
                case SET_ENTITLEMENT -> setEntitlement(tenant, convert(request, SetEntitlement.class));
                case REMOVE_ENTITLEMENT -> removeEntitlement(tenant, convert(request, RemoveEntitlement.class));
            };
        }
    }

    private <T> T convert(CommandRequest request, Class<T> type) {
        if (request.payload() == null || request.payload().isNull()) throw new IllegalArgumentException("command payload is required");
        return objectMapper.convertValue(request.payload(), type);
    }

    private CommandResult addScope(Tenant tenant, AddScope payload) {
        Scope parent = requiredScope(tenant, payload.parentScopeId());
        ScopeData data = Objects.requireNonNull(payload.scope(), "scope is required");
        if (tenant.getScopes().containsKey(data.id())) throw new IllegalArgumentException("scope already exists: " + data.id());
        Scope scope = new Scope(data.id(), data.kind(), data.name(), data.metadata(), parent.getId());
        tenant.getScopes().put(scope.getId(), scope);
        parent.addChild(scope.getId());
        tenantRepository.insertScope(tenant.getId(), scope);
        return ok("scope added: " + scope.getId());
    }

    private CommandResult updateScope(Tenant tenant, UpdateScope payload) {
        Scope scope = requiredScope(tenant, payload.scopeId());
        scope.setKind(payload.kind());
        scope.setName(payload.name());
        if (Boolean.TRUE.equals(payload.replaceMetadata())) {
            scope.replaceMetadata(payload.metadata());
        } else {
            scope.mergeMetadata(payload.metadata());
        }
        tenantRepository.updateScope(tenant.getId(), scope);
        return ok("scope updated: " + scope.getId());
    }

    private CommandResult removeScope(Tenant tenant, RemoveScope payload) {
        Scope scope = requiredScope(tenant, payload.scopeId());
        if (scope.getId().equals(tenant.getRootScopeId())) throw new IllegalArgumentException("root scope cannot be removed");

        invalidator.invalidateScopeSubtree(tenant, scope.getId());

        Set<String> scopeIds = new LinkedHashSet<>();
        collectScopeIds(tenant, scope.getId(), scopeIds);
        Set<String> subjectIds = new LinkedHashSet<>();
        for (String scopeId : scopeIds) subjectIds.addAll(tenant.getScopes().get(scopeId).getSubjectIds());

        Scope parent = requiredScope(tenant, scope.getParentScopeId());
        parent.removeChild(scope.getId());

        List<String> removedGrantIds = tenant.getGrants().values().stream()
                .filter(grant ->
                        (grant.target().type() == TargetType.SCOPE && scopeIds.contains(grant.target().id()))
                                || (grant.target().type() == TargetType.SUBJECT && subjectIds.contains(grant.target().id())))
                .map(EntitlementGrant::id)
                .toList();
        removedGrantIds.forEach(grantId -> purgeGrant(tenant, grantId));

        subjectIds.forEach(tenant.getSubjects()::remove);
        scopeIds.forEach(tenant.getScopes()::remove);
        tenantRepository.deleteSubjects(tenant.getId(), subjectIds);
        tenantRepository.deleteScopes(tenant.getId(), scopeIds);
        return ok("scope subtree removed: " + payload.scopeId());
    }

    private CommandResult moveScope(Tenant tenant, MoveScope payload) {
        Scope scope = requiredScope(tenant, payload.scopeId());
        Scope newParent = requiredScope(tenant, payload.newParentScopeId());
        if (scope.getId().equals(tenant.getRootScopeId())) throw new IllegalArgumentException("root scope cannot be moved");
        if (scope.getId().equals(newParent.getId()) || isDescendant(tenant, newParent.getId(), scope.getId())) {
            throw new IllegalArgumentException("scope move would create a cycle");
        }
        Scope oldParent = requiredScope(tenant, scope.getParentScopeId());
        oldParent.removeChild(scope.getId());
        newParent.addChild(scope.getId());
        scope.setParentScopeId(newParent.getId());
        tenantRepository.updateScopeParent(tenant.getId(), scope.getId(), newParent.getId());
        invalidator.invalidateScopeSubtree(tenant, scope.getId());
        return ok("scope moved: " + scope.getId());
    }

    private CommandResult addSubject(Tenant tenant, AddSubject payload) {
        Scope scope = requiredScope(tenant, payload.scopeId());
        var input = Objects.requireNonNull(payload.subject(), "subject is required");
        if (tenant.getSubjects().containsKey(input.id())) throw new IllegalArgumentException("subject already exists: " + input.id());
        Subject subject = new Subject(input.id(), input.kind(), input.name(), input.metadata(), scope.getId());
        tenant.getSubjects().put(subject.getId(), subject);
        scope.addSubject(subject.getId());
        tenantRepository.insertSubject(tenant.getId(), subject);
        return ok("subject added: " + subject.getId());
    }

    private CommandResult updateSubject(Tenant tenant, UpdateSubject payload) {
        Subject subject = requiredSubject(tenant, payload.subjectId());
        subject.setKind(payload.kind());
        subject.setName(payload.name());
        if (Boolean.TRUE.equals(payload.replaceMetadata())) {
            subject.replaceMetadata(payload.metadata());
        } else {
            subject.mergeMetadata(payload.metadata());
        }
        tenantRepository.updateSubject(tenant.getId(), subject);
        return ok("subject updated: " + subject.getId());
    }

    private CommandResult removeSubject(Tenant tenant, RemoveSubject payload) {
        Subject subject = requiredSubject(tenant, payload.subjectId());
        cache.invalidateSubject(tenant.getId(), subject.getId());
        requiredScope(tenant, subject.getScopeId()).removeSubject(subject.getId());
        tenant.getSubjects().remove(subject.getId());
        List<String> removedGrantIds = tenant.getGrants().values().stream()
                .filter(grant -> grant.target().type() == TargetType.SUBJECT && grant.target().id().equals(subject.getId()))
                .map(EntitlementGrant::id)
                .toList();
        removedGrantIds.forEach(grantId -> purgeGrant(tenant, grantId));
        tenantRepository.deleteSubjects(tenant.getId(), List.of(subject.getId()));
        return ok("subject removed: " + subject.getId());
    }

    private CommandResult moveSubject(Tenant tenant, MoveSubject payload) {
        Subject subject = requiredSubject(tenant, payload.subjectId());
        Scope oldScope = requiredScope(tenant, subject.getScopeId());
        Scope newScope = requiredScope(tenant, payload.newScopeId());
        oldScope.removeSubject(subject.getId());
        newScope.addSubject(subject.getId());
        subject.setScopeId(newScope.getId());
        tenantRepository.updateSubjectScope(tenant.getId(), subject.getId(), newScope.getId());
        cache.invalidateSubject(tenant.getId(), subject.getId());
        return ok("subject moved: " + subject.getId());
    }

    private CommandResult addResource(Tenant tenant, AddResource payload) {
        Resource resource = Objects.requireNonNull(payload.resource(), "resource is required");
        ModelValidation.validateResource(resource);
        if (tenant.getResources().putIfAbsent(resource.id(), resource) != null) {
            throw new IllegalArgumentException("resource already exists: " + resource.id());
        }
        tenantRepository.insertResource(tenant.getId(), resource);
        return ok("resource added: " + resource.id());
    }

    private CommandResult updateResource(Tenant tenant, UpdateResource payload) {
        Resource current = requiredResource(tenant, payload.resourceId());
        boolean replace = Boolean.TRUE.equals(payload.replace());
        Map<String, Object> metadata;
        Map<String, EntitlementValue> properties;
        List<EntitlementDefinition> definitions;
        boolean definitionsChanged;
        if (replace) {
            metadata = payload.metadata() == null ? Map.of() : payload.metadata();
            properties = payload.properties() == null ? Map.of() : payload.properties();
            definitions = payload.entitlementDefinitions() == null ? List.of() : payload.entitlementDefinitions();
            definitionsChanged = !current.entitlementDefinitions().equals(definitions);
        } else {
            metadata = new LinkedHashMap<>(current.metadata());
            if (payload.metadata() != null) metadata.putAll(payload.metadata());
            properties = new LinkedHashMap<>(current.properties());
            if (payload.properties() != null) properties.putAll(payload.properties());
            definitionsChanged = payload.entitlementDefinitions() != null;
            definitions = definitionsChanged ? payload.entitlementDefinitions() : current.entitlementDefinitions();
        }

        Resource updated = new Resource(
                current.id(),
                payload.kind() == null || payload.kind().isBlank() ? current.kind() : payload.kind(),
                payload.name() == null || payload.name().isBlank() ? current.name() : payload.name(),
                metadata,
                properties,
                definitions);
        ModelValidation.validateResource(updated);

        for (EntitlementGrant grant : tenant.getGrants().values()) {
            if (grant.resourceId().equals(updated.id())) {
                EntitlementDefinition definition = updated.definition(grant.entitlementKey());
                if (definition == null || definition.valueType() != grant.value().valueType()) {
                    throw new IllegalArgumentException("resource update would invalidate existing grant: " + grant.id());
                }
            }
        }
        tenant.getResources().put(updated.id(), updated);
        tenantRepository.updateResource(tenant.getId(), updated);
        if (definitionsChanged) {
            cache.invalidateResource(tenant.getId(), updated.id());
        }
        return ok("resource updated: " + updated.id());
    }

    private CommandResult removeResource(Tenant tenant, RemoveResource payload) {
        requiredResource(tenant, payload.resourceId());
        cache.invalidateResource(tenant.getId(), payload.resourceId());
        tenant.getResources().remove(payload.resourceId());
        List<String> removedGrantIds = tenant.getGrants().values().stream()
                .filter(grant -> grant.resourceId().equals(payload.resourceId()))
                .map(EntitlementGrant::id)
                .toList();
        removedGrantIds.forEach(grantId -> purgeGrant(tenant, grantId));
        tenantRepository.deleteResource(tenant.getId(), payload.resourceId());
        return ok("resource removed: " + payload.resourceId());
    }

    private CommandResult setEntitlement(Tenant tenant, SetEntitlement payload) {
        String id = payload.grantId() == null || payload.grantId().isBlank() ? UUID.randomUUID().toString() : payload.grantId();
        EntitlementGrant grant = new EntitlementGrant(id, payload.target(), payload.resourceId(), payload.entitlementKey(), payload.value());
        ModelValidation.validateGrant(tenant, grant);

        EntitlementGrant existing = ModelValidation.findExactGrant(tenant, grant.target(), grant.resourceId(), grant.entitlementKey());
        boolean unchanged = existing != null
                && existing.id().equals(grant.id())
                && Objects.equals(existing.value(), grant.value());
        boolean preservedRuntime = false;
        if (existing != null) {
            boolean sameId = existing.id().equals(grant.id());
            boolean material = isMaterialRuntimeChange(existing.value(), grant.value());
            tenant.removeGrant(existing.id());
            if (!sameId) {
                tenantRepository.deleteGrant(tenant.getId(), existing.id());
            }
            if (!sameId || material) {
                usageStore.remove(tenant.getId(), existing.id());
                rateLimitService.removeBucket(tenant.getId(), existing.id());
            } else {
                preservedRuntime = true;
            }
        }

        if (tenant.getGrants().containsKey(id)) {
            throw new IllegalArgumentException("grant id already exists: " + id);
        }
        tenant.putGrant(grant);
        tenantRepository.upsertGrant(tenant.getId(), grant);
        if (grant.value() instanceof RateLimitValue && !preservedRuntime) {
            rateLimitService.removeBucket(tenant.getId(), grant.id());
        }
        invalidateEntitlementTarget(tenant, grant.target(), grant.resourceId(), grant.entitlementKey());
        if (!unchanged) {
            if (existing == null) {
                historyService.recordCreated(tenant.getId(), grant);
            } else {
                historyService.recordUpdated(tenant.getId(), existing, grant);
            }
        }
        return ok(existing == null ? "entitlement created: " + grant.id() : "entitlement replaced: " + grant.id());
    }

    private CommandResult removeEntitlement(Tenant tenant, RemoveEntitlement payload) {
        EntitlementGrant existing = ModelValidation.findExactGrant(tenant, payload.target(), payload.resourceId(), payload.entitlementKey());
        if (existing == null) throw new NoSuchElementException("entitlement grant not found");
        purgeGrant(tenant, existing.id());
        invalidateEntitlementTarget(tenant, payload.target(), payload.resourceId(), payload.entitlementKey());
        return ok("entitlement removed: " + existing.id());
    }

    private void purgeGrant(Tenant tenant, String grantId) {
        Optional<EntitlementGrant> removed = tenant.removeGrant(grantId);
        usageStore.remove(tenant.getId(), grantId);
        rateLimitService.removeBucket(tenant.getId(), grantId);
        tenantRepository.deleteGrant(tenant.getId(), grantId);
        removed.ifPresent(grant -> historyService.recordRemoved(tenant.getId(), grant));
    }

    /**
     * Consumable / rate-limit configuration changes reset that grant's runtime state.
     * Equal quota/rate-limit values (same grant id upsert) preserve usage / bucket state.
     */
    static boolean isMaterialRuntimeChange(EntitlementValue previous, EntitlementValue next) {
        if (previous instanceof QuotaValue oldQuota && next instanceof QuotaValue newQuota) {
            return oldQuota.limit().compareTo(newQuota.limit()) != 0
                    || !oldQuota.unit().equals(newQuota.unit())
                    || oldQuota.period() != newQuota.period();
        }
        if (previous instanceof RateLimitValue oldRate && next instanceof RateLimitValue newRate) {
            return oldRate.capacity().compareTo(newRate.capacity()) != 0
                    || oldRate.refillTokens().compareTo(newRate.refillTokens()) != 0
                    || !oldRate.refillPeriod().equals(newRate.refillPeriod());
        }
        return !Objects.equals(previous, next);
    }

    private void invalidateEntitlementTarget(Tenant tenant, Target target, String resourceId, String entitlementKey) {
        if (target.type() == TargetType.SUBJECT) {
            cache.invalidateSubjectEntitlement(tenant.getId(), target.id(), resourceId, entitlementKey);
        } else {
            invalidator.invalidateScopeEntitlement(tenant, target.id(), resourceId, entitlementKey);
        }
    }

    private Scope requiredScope(Tenant tenant, String id) {
        Scope scope = tenant.getScopes().get(id);
        if (scope == null) throw new NoSuchElementException("scope not found: " + id);
        return scope;
    }

    private Subject requiredSubject(Tenant tenant, String id) {
        Subject subject = tenant.getSubjects().get(id);
        if (subject == null) throw new NoSuchElementException("subject not found: " + id);
        return subject;
    }

    private Resource requiredResource(Tenant tenant, String id) {
        Resource resource = tenant.getResources().get(id);
        if (resource == null) throw new NoSuchElementException("resource not found: " + id);
        return resource;
    }

    private void collectScopeIds(Tenant tenant, String scopeId, Set<String> result) {
        if (!result.add(scopeId)) return;
        Scope scope = requiredScope(tenant, scopeId);
        for (String childId : scope.getChildScopeIds()) collectScopeIds(tenant, childId, result);
    }

    private boolean isDescendant(Tenant tenant, String possibleDescendantId, String ancestorId) {
        String current = possibleDescendantId;
        while (current != null) {
            if (current.equals(ancestorId)) return true;
            Scope scope = tenant.getScopes().get(current);
            current = scope == null ? null : scope.getParentScopeId();
        }
        return false;
    }

    private CommandResult ok(String message) {
        return new CommandResult(true, message);
    }
}
