export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
}

export interface CsrfTokenResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

export interface AuthMeResponse {
  authenticated: boolean;
  tenantId: string | null;
  email: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AdminRegistrationInput {
  email: string;
  password: string;
}

export interface TenantInput {
  id: string;
  name: string;
}

export interface SubjectInput {
  id: string;
  kind: string;
  name: string;
  metadata?: Record<string, unknown>;
}

export interface ScopeInput {
  id: string;
  kind: string;
  name: string;
  metadata?: Record<string, unknown>;
  children?: ScopeInput[];
  subjects?: SubjectInput[];
}

export interface OrganizationConfigInput {
  tenant: TenantInput;
  structure: ScopeInput;
}

export interface ResourceInput {
  id: string;
  kind: string;
  name: string;
  metadata?: Record<string, unknown>;
  properties?: Record<string, EntitlementValue>;
  entitlementDefinitions?: EntitlementDefinition[];
}

export interface ResourcesConfigInput {
  resources: ResourceInput[];
}

export interface GrantInput {
  id: string;
  target: Target;
  resourceId: string;
  entitlementKey: string;
  value: EntitlementValue;
}

export interface GrantsConfigInput {
  grants: GrantInput[];
}

export interface CompanyRegistrationRequest {
  organization: OrganizationConfigInput;
  resources: ResourcesConfigInput;
  grants: GrantsConfigInput;
}

export interface CompanySignupRequest {
  admin: AdminRegistrationInput;
  registration: CompanyRegistrationRequest;
}

export interface CompanySignupResponse {
  tenantId: string;
  admin: { email: string };
  apiKey: string;
}

export interface RegistrationPreview {
  valid: boolean;
  summary: {
    scopeCount: number;
    subjectCount: number;
    resourceCount: number;
    entitlementDefinitionCount: number;
    grantCount: number;
    invalidGrantCount: number;
    errorCount: number;
    warningCount: number;
  };
  issues: ConfigurationValidationIssue[];
}

export type ValidationSeverity = "WARNING" | "ERROR";
export type ValidationDomain = "ORGANIZATION" | "RESOURCES" | "GRANTS";

export interface ConfigurationValidationIssue {
  severity: ValidationSeverity;
  code: string;
  domain: ValidationDomain;
  entityType: string;
  entityId: string;
  message: string;
  relatedEntityIds: string[];
}

export type SyncMode = "MERGE" | "RECONCILE";

export interface OrganizationSyncInput {
  mode: SyncMode;
  structure: ScopeInput;
}

export interface ResourcesSyncInput {
  mode: SyncMode;
  resources: ResourceInput[];
}

export interface GrantsSyncInput {
  mode: SyncMode;
  grants: GrantInput[];
}

export interface BulkSyncRequest {
  organization?: OrganizationSyncInput | null;
  resources?: ResourcesSyncInput | null;
  grants?: GrantsSyncInput | null;
}

export interface BulkSyncPreview {
  valid: boolean;
  domains: ValidationDomain[];
  summary: {
    scopesAdded: number;
    scopesUpdated: number;
    scopesMoved: number;
    scopesRemoved: number;
    subjectsAdded: number;
    subjectsUpdated: number;
    subjectsMoved: number;
    subjectsRemoved: number;
    resourcesAdded: number;
    resourcesUpdated: number;
    resourcesRemoved: number;
    grantsCreated: number;
    grantsUpdated: number;
    grantsRemoved: number;
    grantsAutomaticallyRemoved: number;
    invalidGrantCount: number;
    warningCount: number;
    errorCount: number;
  };
  impactSummary: {
    grantsAffected: number;
    grantsAutomaticallyRemoved: number;
  };
  changes: { type: string; entityType: string; entityId: string; message: string }[];
  issues: ConfigurationValidationIssue[];
}

export interface ApiKeyMetadataResponse {
  publicId: string;
  displayPrefix: string;
  createdAt: string;
  rotatedAt: string | null;
}

export interface ApiKeyRotationResponse {
  apiKey: string;
  publicId: string;
  rotatedAt: string;
}

export interface CommandRequest {
  type: CommandType;
  tenantId: string;
  payload: unknown;
}

export type CommandType =
  | "ADD_SCOPE"
  | "UPDATE_SCOPE"
  | "REMOVE_SCOPE"
  | "MOVE_SCOPE"
  | "ADD_SUBJECT"
  | "UPDATE_SUBJECT"
  | "REMOVE_SUBJECT"
  | "MOVE_SUBJECT"
  | "ADD_RESOURCE"
  | "UPDATE_RESOURCE"
  | "REMOVE_RESOURCE"
  | "SET_ENTITLEMENT"
  | "REMOVE_ENTITLEMENT";

export interface CommandResult {
  success: boolean;
  message: string;
}

export interface Tenant {
  id: string;
  name: string;
  rootScopeId: string;
  scopes: Record<string, Scope>;
  subjects: Record<string, Subject>;
  resources: Record<string, Resource>;
  grants: Record<string, EntitlementGrant>;
}

export interface Scope {
  id: string;
  kind: string;
  name: string;
  metadata: Record<string, unknown>;
  parentScopeId: string | null;
  childScopeIds: string[];
  subjectIds: string[];
}

export interface Subject {
  id: string;
  kind: string;
  name: string;
  metadata: Record<string, unknown>;
  scopeId: string;
}

export interface Resource {
  id: string;
  kind: string;
  name: string;
  metadata: Record<string, unknown>;
  properties: Record<string, EntitlementValue>;
  entitlementDefinitions: EntitlementDefinition[];
}

export interface EntitlementDefinition {
  key: string;
  name: string;
  valueType: EntitlementValueType;
}

export type EntitlementValueType =
  | "BOOLEAN"
  | "QUANTITY"
  | "QUOTA"
  | "RATE_LIMIT"
  | "RANGE"
  | "TIME_RANGE"
  | "SET"
  | "TEXT";

export type QuotaPeriod = "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";
export type TargetType = "SCOPE" | "SUBJECT";

export interface Target {
  type: TargetType;
  id: string;
}

export interface EntitlementGrant {
  id: string;
  target: Target;
  resourceId: string;
  entitlementKey: string;
  value: EntitlementValue;
}

export type EntitlementValue =
  | { type: "BOOLEAN"; value: boolean; valueType?: "BOOLEAN" }
  | { type: "QUANTITY"; value: number | string; unit: string; valueType?: "QUANTITY" }
  | { type: "QUOTA"; limit: number | string; unit: string; period: QuotaPeriod; valueType?: "QUOTA" }
  | {
      type: "RATE_LIMIT";
      capacity: number | string;
      refillTokens: number | string;
      refillPeriod: string;
      valueType?: "RATE_LIMIT";
    }
  | { type: "RANGE"; min: number | string; max: number | string; unit: string; valueType?: "RANGE" }
  | { type: "TIME_RANGE"; from: string; until: string; valueType?: "TIME_RANGE" }
  | { type: "SET"; values: string[]; valueType?: "SET" }
  | { type: "TEXT"; value: string; valueType?: "TEXT" };

export interface ResourceLiveResult {
  resourceId: string;
  resourceName: string;
  observedAt: string;
  entitlements: EntitlementLive[];
}

export interface EntitlementLive {
  entitlementKey: string;
  entitlementName: string;
  valueType: EntitlementValueType;
  grants: GrantLive[];
}

export interface GrantLive {
  grantId: string;
  source: Target;
  value: EntitlementValue;
  runtime: RuntimeState | null;
  entitledSubjectCount: number;
  active: boolean;
}

export type RuntimeState = {
  type?: EntitlementValueType;
  limit?: number | string;
  unit?: string;
  period?: QuotaPeriod;
  consumed?: number | string;
  remaining?: number | string;
  periodStart?: string;
  periodEnd?: string;
  value?: boolean | number | string;
  min?: number | string;
  max?: number | string;
  from?: string;
  until?: string;
  active?: boolean;
  timeRemaining?: string;
  values?: string[];
  capacity?: number | string;
  refillTokens?: number | string;
  refillPeriod?: string;
  availableTokens?: number | string;
};

export interface EntitlementHistoryEvent {
  id: string;
  tenantId: string;
  resourceId: string;
  entitlementKey: string;
  target: Target;
  changeType: "CREATED" | "UPDATED" | "REMOVED";
  previousGrantId: string | null;
  newGrantId: string | null;
  oldValue: EntitlementValue | null;
  newValue: EntitlementValue | null;
  changedAt: string;
}

export interface TenantEntitlementHistory {
  changes: EntitlementHistoryEvent[];
}

export interface TenantUsageHistory {
  resources: ResourceUsageHistory[];
}

export interface ResourceUsageHistory {
  resourceId: string;
  resourceName: string;
  resourceKind: string;
  entitlements: EntitlementUsage[];
}

export interface EntitlementUsage {
  entitlementKey: string;
  grants: GrantUsage[];
}

export interface GrantUsage {
  grantId: string;
  grantTarget: Target;
  grantTargetNameAtTime: string;
  usage: UsageRecord[];
}

export type UsageRecord = BucketUsage | EventUsage;

export interface BucketUsage {
  type: "BUCKET";
  subjectId: string;
  subjectNameAtTime: string;
  bucketStart: string;
  bucketEnd: string;
  totalConsumed: number | string;
  operationCount: number;
  firstOccurredAt: string;
  lastOccurredAt: string;
}

export interface EventUsage {
  type: "EVENT";
  subjectId: string;
  subjectNameAtTime: string;
  occurredAt: string;
  usedValue: unknown;
}
