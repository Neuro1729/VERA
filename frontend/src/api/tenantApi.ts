import { apiRequest } from "./client";
import type { Tenant } from "./types";

export async function fetchTenant(tenantId: string): Promise<Tenant> {
  return apiRequest<Tenant>(`/api/tenants/${encodeURIComponent(tenantId)}`, { authRequired: true });
}
