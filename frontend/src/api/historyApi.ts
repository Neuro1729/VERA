import { apiRequest } from "./client";
import type { TenantEntitlementHistory, TenantUsageHistory } from "./types";

export async function fetchEntitlementHistory(tenantId: string): Promise<TenantEntitlementHistory> {
  return apiRequest<TenantEntitlementHistory>(
    `/api/tenants/${encodeURIComponent(tenantId)}/entitlement-history`,
    { authRequired: true },
  );
}

export async function fetchUsageHistory(tenantId: string): Promise<TenantUsageHistory> {
  return apiRequest<TenantUsageHistory>(
    `/api/tenants/${encodeURIComponent(tenantId)}/usage-history`,
    { authRequired: true },
  );
}
