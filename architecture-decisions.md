# Architecture decisions

VERA models access as a tenant-owned hierarchy. The engine does not know departments, employees, GPUs, or universities; those are tenant-defined `kind` strings.

```text
Tenant
└── Root Scope
    ├── Scope
    │   ├── Scope
    │   │   └── Subject
    │   └── Subject
    └── Scope
        └── Subject

Resource catalog ── EntitlementDefinition (grantable keys)
Scope or Subject ── EntitlementGrant ── Resource + EntitlementValue
Winning grant    ── Usage / rate-limit pool
```

## Concepts

| Concept | Role |
| --- | --- |
| Tenant | Isolated customer world |
| Scope | Recursive grouping (`parentScopeId` / `childScopeIds`) |
| Subject | Actor that resolves entitlements (`scopeId`) |
| Resource | Catalog entry with entitlement definitions |
| EntitlementGrant | One typed value for one key, on one scope or subject |
| Usage | Consumed amount for a quota grant |

`kind` is a string, not an enum. Do not add `Department`, `Team`, `Employee`, or similar Java types.

A resource **property** describes the resource (`capacity = 100 GPU`). A **grant** assigns what a scope or subject may use. V1 does not require grants to sum to physical capacity.

There is no `TENANT` grant target. A company-wide default is a grant on the root scope so inheritance stays one algorithm.

One grant is one entitlement key. `gpu.enabled`, `gpu.hours`, and `gpu.maxConcurrent` are three grants even if they share a target.

## Resolution

Walk outward from the subject. First matching grant wins:

```text
subject → current scope → parent scopes → root
```

No additive / min / max / deny-wins strategies in V1.

Quota and rate-limit pools belong to the **winning grant**, not the caller. Two subjects who resolve to the same department grant share that pool. A subject-level grant creates a separate pool.

## Value types

BOOLEAN, QUANTITY, QUOTA (DAILY/WEEKLY/MONTHLY/YEARLY, UTC calendar windows), RANGE, TIME_RANGE, SET, TEXT.

Only QUOTA is consumed. TIME_RANGE is checked against an injected `Clock` at evaluation time; there is no scheduler.

## Persistence

PostgreSQL is the durable store (`entitlements`). Flyway owns the schema (`src/main/resources/db/migration/`). Unit tests use the in-memory profile. Postgres integration tests use `entitlements_test`.

Quota consume locks the `usage_current` row (`SELECT … FOR UPDATE`) then upserts. History tables (`entitlement_history`, `usage_events`, `usage_buckets`) are independent of live grant rows so audit survives deletes.

## Auth

Two actors:

- One human admin per tenant: email/password, server `HttpSession`, `JSESSIONID`.
- One company backend per tenant: API key header `X-VERA-API-KEY`, `/api/gateway/**` only.

No JWT, OAuth, OIDC, or multiple admins per tenant. Raw API keys are shown once; the database stores `publicId` + `secretHash`. Path/body `tenantId` is not authentication.

## Configuration

Three documents: organization, resources, grants. Preview is side-effect free. Secure create is `POST /api/auth/signup`.

| Change | API |
| --- | --- |
| Small edit | `POST /api/commands` |
| One domain catalog | `…/sync/organization` / `resources` / `grants` |
| Coordinated change | `POST /api/tenants/{id}/sync` |

Bulk domains use MERGE (patch listed objects) or RECONCILE (submitted document is the desired final state). Validation runs against the projected final tenant, not an isolated JSON slice.

## Runtime

Company backends call gateway evaluate / consume / rate-limit / use. The engine returns ALLOW/DENY/remaining. It does not provision GPUs or call company systems. Quota/rate-limit denial is HTTP 200 with `allowed=false`.

## Invariant

A tenant owns a scope tree of subjects, a resource catalog, and typed grants on scopes or subjects. Nearest grant wins. Consumable usage is stored on that grant, so everyone who resolves to it shares one pool.
