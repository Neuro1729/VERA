import { apiRequest } from "./client";
import { fetchCsrf } from "./authApi";
import type {
  BulkSyncPreview,
  BulkSyncRequest,
  GrantInput,
  ResourceInput,
  ScopeInput,
  SyncMode,
} from "./types";

export async function previewBulkSync(
  tenantId: string,
  request: BulkSyncRequest,
): Promise<BulkSyncPreview> {
  await fetchCsrf();
  return apiRequest<BulkSyncPreview>(
    `/api/tenants/${encodeURIComponent(tenantId)}/sync/preview`,
    { method: "POST", body: request, authRequired: true },
  );
}

export async function applyBulkSync(
  tenantId: string,
  request: BulkSyncRequest,
): Promise<BulkSyncPreview> {
  await fetchCsrf();
  return apiRequest<BulkSyncPreview>(`/api/tenants/${encodeURIComponent(tenantId)}/sync`, {
    method: "POST",
    body: request,
    authRequired: true,
  });
}

export interface DomainDraft {
  selected: boolean;
  mode: SyncMode;
  json: unknown;
}

export function buildBulkSyncRequest(args: {
  organization?: DomainDraft;
  resources?: DomainDraft;
  grants?: DomainDraft;
}): BulkSyncRequest {
  const request: BulkSyncRequest = {};
  if (args.organization?.selected) {
    request.organization = {
      mode: args.organization.mode,
      structure: extractStructure(args.organization.json),
    };
  }
  if (args.resources?.selected) {
    request.resources = {
      mode: args.resources.mode,
      resources: extractResources(args.resources.json),
    };
  }
  if (args.grants?.selected) {
    request.grants = {
      mode: args.grants.mode,
      grants: extractGrants(args.grants.json),
    };
  }
  return request;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function extractStructure(json: unknown): ScopeInput {
  if (!isRecord(json)) throw new Error("Organization JSON must be an object.");
  if ("structure" in json) return json.structure as unknown as ScopeInput;
  return json as unknown as ScopeInput;
}

export function extractResources(json: unknown): ResourceInput[] {
  if (Array.isArray(json)) return json as unknown as ResourceInput[];
  if (!isRecord(json)) throw new Error("Resources JSON must be an object or array.");
  if (Array.isArray(json.resources)) return json.resources as unknown as ResourceInput[];
  throw new Error("Resources JSON must contain a resources array.");
}

export function extractGrants(json: unknown): GrantInput[] {
  if (Array.isArray(json)) return json as unknown as GrantInput[];
  if (!isRecord(json)) throw new Error("Grants JSON must be an object or array.");
  if (Array.isArray(json.grants)) return json.grants as unknown as GrantInput[];
  throw new Error("Grants JSON must contain a grants array.");
}
