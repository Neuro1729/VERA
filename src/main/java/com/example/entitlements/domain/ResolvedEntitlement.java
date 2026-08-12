package com.example.entitlements.domain;

public record ResolvedEntitlement(EntitlementGrant grant) {
    public Target source() {
        return grant.target();
    }
}
