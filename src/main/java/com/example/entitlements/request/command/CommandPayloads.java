package com.example.entitlements.request.command;

import com.example.entitlements.domain.EntitlementDefinition;
import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Target;
import com.example.entitlements.request.SubjectInput;

import java.util.List;
import java.util.Map;

public final class CommandPayloads {
    private CommandPayloads() {}

    public record ScopeData(String id, String kind, String name, Map<String, Object> metadata) {}
    public record AddScope(String parentScopeId, ScopeData scope) {}
    public record UpdateScope(String scopeId, String kind, String name, Map<String, Object> metadata) {}
    public record RemoveScope(String scopeId) {}
    public record MoveScope(String scopeId, String newParentScopeId) {}

    public record AddSubject(String scopeId, SubjectInput subject) {}
    public record UpdateSubject(String subjectId, String kind, String name, Map<String, Object> metadata) {}
    public record RemoveSubject(String subjectId) {}
    public record MoveSubject(String subjectId, String newScopeId) {}

    public record AddResource(Resource resource) {}
    public record UpdateResource(
            String resourceId,
            String kind,
            String name,
            Map<String, Object> metadata,
            Map<String, EntitlementValue> properties,
            List<EntitlementDefinition> entitlementDefinitions
    ) {}
    public record RemoveResource(String resourceId) {}

    public record SetEntitlement(
            String grantId,
            Target target,
            String resourceId,
            String entitlementKey,
            EntitlementValue value
    ) {}
    public record RemoveEntitlement(Target target, String resourceId, String entitlementKey) {}
}
