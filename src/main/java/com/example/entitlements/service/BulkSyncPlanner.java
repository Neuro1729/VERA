package com.example.entitlements.service;

import com.example.entitlements.domain.EntitlementDefinition;
import com.example.entitlements.domain.EntitlementGrant;
import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.GrantLookupKey;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Scope;
import com.example.entitlements.domain.Subject;
import com.example.entitlements.domain.TargetType;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.BulkSyncPreview;
import com.example.entitlements.request.BulkSyncRequest;
import com.example.entitlements.request.CommandRequest;
import com.example.entitlements.request.CommandType;
import com.example.entitlements.request.GrantInput;
import com.example.entitlements.request.GrantsSyncInput;
import com.example.entitlements.request.OrganizationSyncInput;
import com.example.entitlements.request.ResourcesSyncInput;
import com.example.entitlements.request.SubjectInput;
import com.example.entitlements.request.SyncMode;
import com.example.entitlements.request.command.CommandPayloads.AddResource;
import com.example.entitlements.request.command.CommandPayloads.AddScope;
import com.example.entitlements.request.command.CommandPayloads.AddSubject;
import com.example.entitlements.request.command.CommandPayloads.MoveScope;
import com.example.entitlements.request.command.CommandPayloads.MoveSubject;
import com.example.entitlements.request.command.CommandPayloads.RemoveEntitlement;
import com.example.entitlements.request.command.CommandPayloads.RemoveResource;
import com.example.entitlements.request.command.CommandPayloads.RemoveScope;
import com.example.entitlements.request.command.CommandPayloads.RemoveSubject;
import com.example.entitlements.request.command.CommandPayloads.ScopeData;
import com.example.entitlements.request.command.CommandPayloads.SetEntitlement;
import com.example.entitlements.request.command.CommandPayloads.UpdateResource;
import com.example.entitlements.request.command.CommandPayloads.UpdateScope;
import com.example.entitlements.request.command.CommandPayloads.UpdateSubject;
import com.example.entitlements.validation.ConfigurationValidationIssue;
import com.example.entitlements.validation.ValidationDomain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class BulkSyncPlanner {
    private final ObjectMapper objectMapper;

    public BulkSyncPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    PlannedSync plan(Tenant current, BulkSyncRequest request) {
        if (request == null || request.domainCount() == 0) {
            throw new IllegalArgumentException("at least one of organization, resources, or grants is required");
        }
        List<ValidationDomain> domains = new ArrayList<>();
        if (request.hasOrganization()) domains.add(ValidationDomain.ORGANIZATION);
        if (request.hasResources()) domains.add(ValidationDomain.RESOURCES);
        if (request.hasGrants()) domains.add(ValidationDomain.GRANTS);

        List<ConfigurationValidationIssue> intake = new ArrayList<>();
        Tenant projected = TenantCopy.deepCopy(current);

        OrgDiff orgDiff = request.hasOrganization()
                ? diffOrganization(current, request.organization(), intake)
                : OrgDiff.empty();
        ResourceDiff resourceDiff = request.hasResources()
                ? diffResources(current, request.resources(), intake)
                : ResourceDiff.empty();

        applyOrganization(projected, orgDiff);
        applyResources(projected, resourceDiff);

        Set<String> cascadeGrantIds = cascadeRemovedGrantIds(current, projected);
        GrantDiff grantDiff = request.hasGrants()
                ? diffGrants(projected, request.grants(), intake)
                : GrantDiff.empty();
        applyGrants(projected, grantDiff);

        List<CommandRequest> commands = new ArrayList<>();
        List<BulkSyncPreview.Change> changes = new ArrayList<>();
        String tenantId = current.getId();
        emitGrantRemoves(tenantId, grantDiff, commands, changes);
        emitOrganizationAddsUpdatesMoves(tenantId, orgDiff, commands, changes);
        emitResourceAddsUpdates(tenantId, resourceDiff, commands, changes);
        emitGrantUpserts(tenantId, grantDiff, commands, changes);
        emitOrganizationRemoves(tenantId, orgDiff, commands, changes);
        emitResourceRemoves(tenantId, resourceDiff, commands, changes);

        Counts counts = new Counts(
                orgDiff.adds.size(), orgDiff.updates.size(), orgDiff.moves.size(), orgDiff.removedIds.size(),
                orgDiff.subjectAdds.size(), orgDiff.subjectUpdates.size(), orgDiff.subjectMoves.size(),
                orgDiff.removedSubjectIds.size(),
                resourceDiff.adds.size(), resourceDiff.updates.size(), resourceDiff.removes.size(),
                grantDiff.creates.size(), grantDiff.updates.size(), grantDiff.removes.size(),
                cascadeGrantIds.size());
        return new PlannedSync(new BulkSyncPlan(commands), projected, counts, changes, intake, domains, cascadeGrantIds);
    }

    record PlannedSync(
            BulkSyncPlan plan,
            Tenant projected,
            Counts counts,
            List<BulkSyncPreview.Change> changes,
            List<ConfigurationValidationIssue> intakeIssues,
            List<ValidationDomain> domains,
            Set<String> cascadeRemovedGrantIds
    ) {}

    record Counts(
            int scopesAdded, int scopesUpdated, int scopesMoved, int scopesRemoved,
            int subjectsAdded, int subjectsUpdated, int subjectsMoved, int subjectsRemoved,
            int resourcesAdded, int resourcesUpdated, int resourcesRemoved,
            int grantsCreated, int grantsUpdated, int grantsRemoved,
            int grantsAutomaticallyRemoved
    ) {}

    private record ScopeMove(String scopeId, String newParentId, int desiredDepth) {}
    private record SubjectMove(String subjectId, String newScopeId) {}

    private static final class OrgDiff {
        final List<OrganizationIndex.DesiredScope> adds = new ArrayList<>();
        final List<OrganizationIndex.DesiredScope> updates = new ArrayList<>();
        final List<ScopeMove> moves = new ArrayList<>();
        final Set<String> removedIds = new LinkedHashSet<>();
        final List<String> topRemovedIds = new ArrayList<>();
        final List<OrganizationIndex.DesiredSubject> subjectAdds = new ArrayList<>();
        final List<OrganizationIndex.DesiredSubject> subjectUpdates = new ArrayList<>();
        final List<SubjectMove> subjectMoves = new ArrayList<>();
        final Set<String> removedSubjectIds = new LinkedHashSet<>();
        final List<String> explicitRemovedSubjectIds = new ArrayList<>();
        boolean replaceMetadata;

        static OrgDiff empty() {
            return new OrgDiff();
        }
    }

    private static final class ResourceDiff {
        final List<Resource> adds = new ArrayList<>();
        final List<UpdateResource> updates = new ArrayList<>();
        final List<Resource> updatedResources = new ArrayList<>();
        final List<String> removes = new ArrayList<>();
        static ResourceDiff empty() {
            return new ResourceDiff();
        }
    }

    private static final class GrantDiff {
        final List<EntitlementGrant> creates = new ArrayList<>();
        final List<EntitlementGrant> updates = new ArrayList<>();
        final List<EntitlementGrant> removes = new ArrayList<>();
        static GrantDiff empty() {
            return new GrantDiff();
        }
    }

    private OrgDiff diffOrganization(Tenant current, OrganizationSyncInput input, List<ConfigurationValidationIssue> issues) {
        OrgDiff diff = new OrgDiff();
        if (input == null || input.mode() == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.ORGANIZATION, "SYNC", current.getId(),
                    "organization.mode is required."));
            return diff;
        }
        if (input.structure() == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "ROOT_MISSING", ValidationDomain.ORGANIZATION, "SCOPE", null,
                    "organization.structure is required."));
            return diff;
        }
        diff.replaceMetadata = input.mode() == SyncMode.RECONCILE;
        OrganizationIndex index = OrganizationIndex.from(input.structure());
        issues.addAll(index.issues);
        if (index.rootId == null || !index.rootId.equals(current.getRootScopeId())) {
            issues.add(ConfigurationValidationIssue.error(
                    "ROOT_ID_INVALID", ValidationDomain.ORGANIZATION, "SCOPE", index.rootId,
                    "Incoming root must represent the existing root " + current.getRootScopeId() + ".",
                    current.getRootScopeId()));
            return diff;
        }

        for (OrganizationIndex.DesiredScope desired : index.scopes.values()) {
            Scope existing = current.getScopes().get(desired.id());
            if (existing == null) {
                diff.adds.add(desired);
                continue;
            }
            String currentParent = existing.getParentScopeId();
            if (!Objects.equals(currentParent, desired.parentId())) {
                if (desired.id().equals(current.getRootScopeId())) {
                    issues.add(ConfigurationValidationIssue.error(
                            "ROOT_ID_INVALID", ValidationDomain.ORGANIZATION, "SCOPE", desired.id(),
                            "Root scope cannot be moved."));
                } else {
                    diff.moves.add(new ScopeMove(desired.id(), desired.parentId(), desired.depth()));
                }
            }
            if (scopeFieldsChanged(existing, desired, diff.replaceMetadata)) {
                diff.updates.add(desired);
            }
        }

        for (OrganizationIndex.DesiredSubject desired : index.subjects.values()) {
            Subject existing = current.getSubjects().get(desired.id());
            if (existing == null) {
                diff.subjectAdds.add(desired);
                continue;
            }
            if (!Objects.equals(existing.getScopeId(), desired.scopeId())) {
                diff.subjectMoves.add(new SubjectMove(desired.id(), desired.scopeId()));
            }
            if (subjectFieldsChanged(existing, desired, diff.replaceMetadata)) {
                diff.subjectUpdates.add(desired);
            }
        }

        if (input.mode() == SyncMode.RECONCILE) {
            Set<String> removedScopes = new LinkedHashSet<>();
            for (String scopeId : current.getScopes().keySet()) {
                if (!index.scopes.containsKey(scopeId)) {
                    if (scopeId.equals(current.getRootScopeId())) {
                        continue;
                    }
                    removedScopes.add(scopeId);
                }
            }
            diff.removedIds.addAll(removedScopes);
            Set<String> cascadeSubjects = subjectsInScopes(current, expandDescendants(current, removedScopes));
            for (String subjectId : current.getSubjects().keySet()) {
                if (!index.subjects.containsKey(subjectId)) {
                    diff.removedSubjectIds.add(subjectId);
                    if (!cascadeSubjects.contains(subjectId)) {
                        diff.explicitRemovedSubjectIds.add(subjectId);
                    }
                }
            }
            diff.removedSubjectIds.addAll(cascadeSubjects);
            for (String scopeId : removedScopes) {
                Scope scope = current.getScopes().get(scopeId);
                if (scope.getParentScopeId() == null || !removedScopes.contains(scope.getParentScopeId())) {
                    diff.topRemovedIds.add(scopeId);
                }
            }
            diff.topRemovedIds.sort(Comparator.comparingInt(id -> -depthOf(current, id)));
        }

        diff.adds.sort(Comparator.comparingInt(OrganizationIndex.DesiredScope::depth));
        diff.moves.sort(Comparator.comparingInt(ScopeMove::desiredDepth));
        return diff;
    }

    private ResourceDiff diffResources(Tenant current, ResourcesSyncInput input, List<ConfigurationValidationIssue> issues) {
        ResourceDiff diff = new ResourceDiff();
        if (input == null || input.mode() == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.RESOURCES, "SYNC", current.getId(),
                    "resources.mode is required."));
            return diff;
        }
        Map<String, Resource> desired = new LinkedHashMap<>();
        for (Resource resource : safe(input.resources())) {
            if (resource == null) continue;
            if (desired.putIfAbsent(resource.id(), resource) != null) {
                issues.add(ConfigurationValidationIssue.error(
                        "DUPLICATE_RESOURCE_ID", ValidationDomain.RESOURCES, "RESOURCE", resource.id(),
                        "Duplicate resource id: " + resource.id() + "."));
            }
        }
        boolean reconcile = input.mode() == SyncMode.RECONCILE;
        for (Resource incoming : desired.values()) {
            Resource existing = current.getResources().get(incoming.id());
            if (existing == null) {
                diff.adds.add(incoming);
                continue;
            }
            if (reconcile) {
                if (!existing.equals(incoming)) {
                    diff.updates.add(new UpdateResource(
                            incoming.id(), incoming.kind(), incoming.name(),
                            incoming.metadata(), incoming.properties(), incoming.entitlementDefinitions(), true));
                    diff.updatedResources.add(incoming);
                }
            } else {
                Resource merged = mergeResource(existing, incoming);
                if (!merged.equals(existing)) {
                    List<EntitlementDefinition> defs = incoming.entitlementDefinitions().isEmpty()
                            ? null : incoming.entitlementDefinitions();
                    diff.updates.add(new UpdateResource(
                            incoming.id(), incoming.kind(), incoming.name(),
                            incoming.metadata(), incoming.properties(), defs, false));
                    diff.updatedResources.add(merged);
                }
            }
        }
        if (reconcile) {
            for (String resourceId : current.getResources().keySet()) {
                if (!desired.containsKey(resourceId)) {
                    diff.removes.add(resourceId);
                }
            }
        }
        return diff;
    }

    private GrantDiff diffGrants(Tenant projected, GrantsSyncInput input, List<ConfigurationValidationIssue> issues) {
        GrantDiff diff = new GrantDiff();
        if (input == null || input.mode() == null) {
            issues.add(ConfigurationValidationIssue.error(
                    "REQUIRED_FIELD_MISSING", ValidationDomain.GRANTS, "SYNC", projected.getId(),
                    "grants.mode is required."));
            return diff;
        }
        Map<GrantLookupKey, EntitlementGrant> desired = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        for (GrantInput grantInput : safe(input.grants())) {
            try {
                EntitlementGrant existing = projected.findGrant(
                        grantInput.target(), grantInput.resourceId(), grantInput.entitlementKey()).orElse(null);
                String id = resolveGrantId(grantInput.id(), existing);
                if (!ids.add(id)) {
                    issues.add(ConfigurationValidationIssue.error(
                            "DUPLICATE_GRANT_ID", ValidationDomain.GRANTS, "GRANT", id,
                            "Duplicate grant id: " + id + "."));
                    continue;
                }
                EntitlementGrant grant = new EntitlementGrant(
                        id, grantInput.target(), grantInput.resourceId(), grantInput.entitlementKey(), grantInput.value());
                GrantLookupKey key = GrantLookupKey.from(grant);
                if (desired.putIfAbsent(key, grant) != null) {
                    issues.add(ConfigurationValidationIssue.error(
                            "DUPLICATE_LOGICAL_GRANT", ValidationDomain.GRANTS, "GRANT", grant.id(),
                            "Duplicate logical grant for target/resource/entitlement."));
                    continue;
                }
                if (existing == null) {
                    diff.creates.add(grant);
                } else if (!existing.id().equals(grant.id()) || !Objects.equals(existing.value(), grant.value())) {
                    diff.updates.add(grant);
                }
            } catch (RuntimeException ex) {
                issues.add(ConfigurationValidationIssue.error(
                        "INVALID_GRANT", ValidationDomain.GRANTS, "GRANT",
                        grantInput == null ? null : grantInput.id(),
                        ex.getMessage()));
            }
        }
        if (input.mode() == SyncMode.RECONCILE) {
            for (EntitlementGrant current : projected.getGrants().values()) {
                GrantLookupKey key = GrantLookupKey.from(current);
                if (!desired.containsKey(key)) {
                    diff.removes.add(current);
                }
            }
        }
        return diff;
    }

    private void applyOrganization(Tenant projected, OrgDiff diff) {
        for (OrganizationIndex.DesiredScope add : diff.adds) {
            Scope scope = new Scope(add.id(), add.kind(), add.name(), add.metadata(), add.parentId());
            projected.getScopes().put(scope.getId(), scope);
            if (add.parentId() != null) {
                Scope parent = projected.getScopes().get(add.parentId());
                if (parent != null) parent.addChild(scope.getId());
            }
        }
        for (OrganizationIndex.DesiredScope update : diff.updates) {
            Scope scope = projected.getScopes().get(update.id());
            if (scope == null) continue;
            scope.setKind(update.kind());
            scope.setName(update.name());
            if (diff.replaceMetadata) scope.replaceMetadata(update.metadata());
            else scope.mergeMetadata(update.metadata());
        }
        for (ScopeMove move : diff.moves) {
            Scope scope = projected.getScopes().get(move.scopeId());
            Scope newParent = projected.getScopes().get(move.newParentId());
            if (scope == null || newParent == null) continue;
            Scope oldParent = projected.getScopes().get(scope.getParentScopeId());
            if (oldParent != null) oldParent.removeChild(scope.getId());
            newParent.addChild(scope.getId());
            scope.setParentScopeId(newParent.getId());
        }
        for (OrganizationIndex.DesiredSubject add : diff.subjectAdds) {
            Subject subject = new Subject(add.id(), add.kind(), add.name(), add.metadata(), add.scopeId());
            projected.getSubjects().put(subject.getId(), subject);
            Scope scope = projected.getScopes().get(add.scopeId());
            if (scope != null) scope.addSubject(subject.getId());
        }
        for (OrganizationIndex.DesiredSubject update : diff.subjectUpdates) {
            Subject subject = projected.getSubjects().get(update.id());
            if (subject == null) continue;
            subject.setKind(update.kind());
            subject.setName(update.name());
            if (diff.replaceMetadata) subject.replaceMetadata(update.metadata());
            else subject.mergeMetadata(update.metadata());
        }
        for (SubjectMove move : diff.subjectMoves) {
            Subject subject = projected.getSubjects().get(move.subjectId());
            Scope newScope = projected.getScopes().get(move.newScopeId());
            if (subject == null || newScope == null) continue;
            Scope oldScope = projected.getScopes().get(subject.getScopeId());
            if (oldScope != null) oldScope.removeSubject(subject.getId());
            newScope.addSubject(subject.getId());
            subject.setScopeId(newScope.getId());
        }
        for (String subjectId : diff.explicitRemovedSubjectIds) {
            removeSubject(projected, subjectId);
        }
        for (String scopeId : diff.topRemovedIds) {
            if (projected.getScopes().containsKey(scopeId)) {
                removeScopeSubtree(projected, scopeId);
            }
        }
    }

    private void applyResources(Tenant projected, ResourceDiff diff) {
        for (Resource add : diff.adds) {
            projected.getResources().put(add.id(), add);
        }
        for (Resource updated : diff.updatedResources) {
            projected.getResources().put(updated.id(), updated);
        }
        for (String resourceId : diff.removes) {
            projected.getResources().remove(resourceId);
            List<String> grantIds = projected.getGrants().values().stream()
                    .filter(grant -> grant.resourceId().equals(resourceId))
                    .map(EntitlementGrant::id)
                    .toList();
            grantIds.forEach(projected::removeGrant);
        }
    }

    private void applyGrants(Tenant projected, GrantDiff diff) {
        for (EntitlementGrant removed : diff.removes) {
            projected.removeGrant(removed.id());
        }
        for (EntitlementGrant grant : diff.updates) {
            EntitlementGrant existing = projected.findGrant(
                    grant.target(), grant.resourceId(), grant.entitlementKey()).orElse(null);
            if (existing != null) projected.removeGrant(existing.id());
            if (!projected.getGrants().containsKey(grant.id())) {
                projected.putGrant(grant);
            }
        }
        for (EntitlementGrant grant : diff.creates) {
            if (!projected.getGrants().containsKey(grant.id())) {
                projected.putGrant(grant);
            }
        }
    }

    private void emitGrantRemoves(
            String tenantId,
            GrantDiff diff,
            List<CommandRequest> commands,
            List<BulkSyncPreview.Change> changes
    ) {
        for (EntitlementGrant grant : diff.removes) {
            commands.add(command(tenantId, CommandType.REMOVE_ENTITLEMENT,
                    new RemoveEntitlement(grant.target(), grant.resourceId(), grant.entitlementKey())));
            changes.add(new BulkSyncPreview.Change(
                    "REMOVE_ENTITLEMENT", "GRANT", grant.id(), "Remove grant " + grant.id()));
        }
    }

    private void emitOrganizationAddsUpdatesMoves(
            String tenantId,
            OrgDiff diff,
            List<CommandRequest> commands,
            List<BulkSyncPreview.Change> changes
    ) {
        for (OrganizationIndex.DesiredScope add : diff.adds) {
            commands.add(command(tenantId, CommandType.ADD_SCOPE, new AddScope(
                    add.parentId(),
                    new ScopeData(add.id(), add.kind(), add.name(), add.metadata()))));
            changes.add(new BulkSyncPreview.Change("ADD_SCOPE", "SCOPE", add.id(), "Add scope " + add.id()));
        }
        for (OrganizationIndex.DesiredScope update : diff.updates) {
            commands.add(command(tenantId, CommandType.UPDATE_SCOPE, new UpdateScope(
                    update.id(), update.kind(), update.name(), update.metadata(), diff.replaceMetadata)));
            changes.add(new BulkSyncPreview.Change("UPDATE_SCOPE", "SCOPE", update.id(), "Update scope " + update.id()));
        }
        for (ScopeMove move : diff.moves) {
            commands.add(command(tenantId, CommandType.MOVE_SCOPE, new MoveScope(move.scopeId(), move.newParentId())));
            changes.add(new BulkSyncPreview.Change("MOVE_SCOPE", "SCOPE", move.scopeId(),
                    "Move scope " + move.scopeId() + " to " + move.newParentId()));
        }
        for (OrganizationIndex.DesiredSubject add : diff.subjectAdds) {
            commands.add(command(tenantId, CommandType.ADD_SUBJECT, new AddSubject(
                    add.scopeId(),
                    new SubjectInput(add.id(), add.kind(), add.name(), add.metadata()))));
            changes.add(new BulkSyncPreview.Change("ADD_SUBJECT", "SUBJECT", add.id(), "Add subject " + add.id()));
        }
        for (OrganizationIndex.DesiredSubject update : diff.subjectUpdates) {
            commands.add(command(tenantId, CommandType.UPDATE_SUBJECT, new UpdateSubject(
                    update.id(), update.kind(), update.name(), update.metadata(), diff.replaceMetadata)));
            changes.add(new BulkSyncPreview.Change("UPDATE_SUBJECT", "SUBJECT", update.id(), "Update subject " + update.id()));
        }
        for (SubjectMove move : diff.subjectMoves) {
            commands.add(command(tenantId, CommandType.MOVE_SUBJECT, new MoveSubject(move.subjectId(), move.newScopeId())));
            changes.add(new BulkSyncPreview.Change("MOVE_SUBJECT", "SUBJECT", move.subjectId(),
                    "Move subject " + move.subjectId() + " to " + move.newScopeId()));
        }
    }

    private void emitResourceAddsUpdates(
            String tenantId,
            ResourceDiff diff,
            List<CommandRequest> commands,
            List<BulkSyncPreview.Change> changes
    ) {
        for (Resource add : diff.adds) {
            commands.add(command(tenantId, CommandType.ADD_RESOURCE, new AddResource(add)));
            changes.add(new BulkSyncPreview.Change("ADD_RESOURCE", "RESOURCE", add.id(), "Add resource " + add.id()));
        }
        for (UpdateResource update : diff.updates) {
            commands.add(command(tenantId, CommandType.UPDATE_RESOURCE, update));
            changes.add(new BulkSyncPreview.Change("UPDATE_RESOURCE", "RESOURCE", update.resourceId(),
                    "Update resource " + update.resourceId()));
        }
    }

    private void emitGrantUpserts(
            String tenantId,
            GrantDiff diff,
            List<CommandRequest> commands,
            List<BulkSyncPreview.Change> changes
    ) {
        for (EntitlementGrant grant : diff.creates) {
            commands.add(command(tenantId, CommandType.SET_ENTITLEMENT, toSet(grant)));
            changes.add(new BulkSyncPreview.Change("SET_ENTITLEMENT", "GRANT", grant.id(), "Create grant " + grant.id()));
        }
        for (EntitlementGrant grant : diff.updates) {
            commands.add(command(tenantId, CommandType.SET_ENTITLEMENT, toSet(grant)));
            changes.add(new BulkSyncPreview.Change("SET_ENTITLEMENT", "GRANT", grant.id(), "Update grant " + grant.id()));
        }
    }

    private void emitOrganizationRemoves(
            String tenantId,
            OrgDiff diff,
            List<CommandRequest> commands,
            List<BulkSyncPreview.Change> changes
    ) {
        for (String subjectId : diff.explicitRemovedSubjectIds) {
            commands.add(command(tenantId, CommandType.REMOVE_SUBJECT, new RemoveSubject(subjectId)));
            changes.add(new BulkSyncPreview.Change("REMOVE_SUBJECT", "SUBJECT", subjectId, "Remove subject " + subjectId));
        }
        for (String scopeId : diff.topRemovedIds) {
            commands.add(command(tenantId, CommandType.REMOVE_SCOPE, new RemoveScope(scopeId)));
            changes.add(new BulkSyncPreview.Change("REMOVE_SCOPE", "SCOPE", scopeId, "Remove scope subtree " + scopeId));
        }
    }

    private void emitResourceRemoves(
            String tenantId,
            ResourceDiff diff,
            List<CommandRequest> commands,
            List<BulkSyncPreview.Change> changes
    ) {
        for (String resourceId : diff.removes) {
            commands.add(command(tenantId, CommandType.REMOVE_RESOURCE, new RemoveResource(resourceId)));
            changes.add(new BulkSyncPreview.Change("REMOVE_RESOURCE", "RESOURCE", resourceId,
                    "Remove resource " + resourceId));
        }
    }

    private CommandRequest command(String tenantId, CommandType type, Object payload) {
        return new CommandRequest(type, tenantId, objectMapper.valueToTree(payload));
    }

    private static SetEntitlement toSet(EntitlementGrant grant) {
        return new SetEntitlement(grant.id(), grant.target(), grant.resourceId(), grant.entitlementKey(), grant.value());
    }

    private static String resolveGrantId(String incomingId, EntitlementGrant existing) {
        if (incomingId != null && !incomingId.isBlank()) return incomingId;
        if (existing != null) return existing.id();
        return UUID.randomUUID().toString();
    }

    private static Resource mergeResource(Resource current, Resource desired) {
        Map<String, Object> metadata = new LinkedHashMap<>(current.metadata());
        metadata.putAll(desired.metadata());
        Map<String, EntitlementValue> properties = new LinkedHashMap<>(current.properties());
        properties.putAll(desired.properties());
        List<EntitlementDefinition> definitions = desired.entitlementDefinitions().isEmpty()
                ? current.entitlementDefinitions() : desired.entitlementDefinitions();
        return new Resource(current.id(), desired.kind(), desired.name(), metadata, properties, definitions);
    }

    private static boolean scopeFieldsChanged(
            Scope existing,
            OrganizationIndex.DesiredScope desired,
            boolean replaceMetadata
    ) {
        if (!existing.getKind().equals(desired.kind()) || !existing.getName().equals(desired.name())) return true;
        if (replaceMetadata) return !existing.getMetadata().equals(desired.metadata());
        for (Map.Entry<String, Object> entry : desired.metadata().entrySet()) {
            if (!Objects.equals(existing.getMetadata().get(entry.getKey()), entry.getValue())) return true;
        }
        return false;
    }

    private static boolean subjectFieldsChanged(
            Subject existing,
            OrganizationIndex.DesiredSubject desired,
            boolean replaceMetadata
    ) {
        if (!existing.getKind().equals(desired.kind()) || !existing.getName().equals(desired.name())) return true;
        if (replaceMetadata) return !existing.getMetadata().equals(desired.metadata());
        for (Map.Entry<String, Object> entry : desired.metadata().entrySet()) {
            if (!Objects.equals(existing.getMetadata().get(entry.getKey()), entry.getValue())) return true;
        }
        return false;
    }

    private static Set<String> cascadeRemovedGrantIds(Tenant current, Tenant projected) {
        Set<String> remaining = new HashSet<>(projected.getGrants().keySet());
        Set<String> removed = new LinkedHashSet<>();
        for (String grantId : current.getGrants().keySet()) {
            if (!remaining.contains(grantId)) removed.add(grantId);
        }
        return removed;
    }

    private static void removeSubject(Tenant tenant, String subjectId) {
        Subject subject = tenant.getSubjects().remove(subjectId);
        if (subject == null) return;
        Scope scope = tenant.getScopes().get(subject.getScopeId());
        if (scope != null) scope.removeSubject(subjectId);
        List<String> grantIds = tenant.getGrants().values().stream()
                .filter(grant -> grant.target().type() == TargetType.SUBJECT && grant.target().id().equals(subjectId))
                .map(EntitlementGrant::id)
                .toList();
        grantIds.forEach(tenant::removeGrant);
    }

    private static void removeScopeSubtree(Tenant tenant, String scopeId) {
        Scope scope = tenant.getScopes().get(scopeId);
        if (scope == null) return;
        Set<String> scopeIds = new LinkedHashSet<>();
        collectScopeIds(tenant, scopeId, scopeIds);
        Set<String> subjectIds = new LinkedHashSet<>();
        for (String id : scopeIds) {
            Scope current = tenant.getScopes().get(id);
            if (current != null) subjectIds.addAll(current.getSubjectIds());
        }
        if (scope.getParentScopeId() != null) {
            Scope parent = tenant.getScopes().get(scope.getParentScopeId());
            if (parent != null) parent.removeChild(scopeId);
        }
        List<String> grantIds = tenant.getGrants().values().stream()
                .filter(grant ->
                        (grant.target().type() == TargetType.SCOPE && scopeIds.contains(grant.target().id()))
                                || (grant.target().type() == TargetType.SUBJECT && subjectIds.contains(grant.target().id())))
                .map(EntitlementGrant::id)
                .toList();
        grantIds.forEach(tenant::removeGrant);
        subjectIds.forEach(tenant.getSubjects()::remove);
        scopeIds.forEach(tenant.getScopes()::remove);
    }

    private static void collectScopeIds(Tenant tenant, String scopeId, Set<String> result) {
        if (!result.add(scopeId)) return;
        Scope scope = tenant.getScopes().get(scopeId);
        if (scope == null) return;
        for (String childId : scope.getChildScopeIds()) collectScopeIds(tenant, childId, result);
    }

    private static Set<String> expandDescendants(Tenant tenant, Set<String> roots) {
        Set<String> all = new LinkedHashSet<>();
        for (String root : roots) collectScopeIds(tenant, root, all);
        return all;
    }

    private static Set<String> subjectsInScopes(Tenant tenant, Set<String> scopeIds) {
        Set<String> subjects = new LinkedHashSet<>();
        for (String scopeId : scopeIds) {
            Scope scope = tenant.getScopes().get(scopeId);
            if (scope != null) subjects.addAll(scope.getSubjectIds());
        }
        return subjects;
    }

    private static int depthOf(Tenant tenant, String scopeId) {
        int depth = 0;
        String current = scopeId;
        while (current != null) {
            Scope scope = tenant.getScopes().get(current);
            if (scope == null) break;
            current = scope.getParentScopeId();
            depth++;
        }
        return depth;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
