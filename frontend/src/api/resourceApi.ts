import { apiRequest } from "./client";
import type { ResourceLiveResult } from "./types";

export async function fetchResourceLive(
  tenantId: string,
  resourceId: string,
): Promise<ResourceLiveResult> {
  return apiRequest<ResourceLiveResult>(
    `/api/tenants/${encodeURIComponent(tenantId)}/resources/${encodeURIComponent(resourceId)}/live`,
    { authRequired: true },
  );
}
