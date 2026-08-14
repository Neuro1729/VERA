package com.example.entitlements.request;

public record BulkSyncRequest(
        OrganizationSyncInput organization,
        ResourcesSyncInput resources,
        GrantsSyncInput grants
) {
    public boolean hasOrganization() {
        return organization != null;
    }

    public boolean hasResources() {
        return resources != null;
    }

    public boolean hasGrants() {
        return grants != null;
    }

    public int domainCount() {
        int count = 0;
        if (hasOrganization()) count++;
        if (hasResources()) count++;
        if (hasGrants()) count++;
        return count;
    }
}
