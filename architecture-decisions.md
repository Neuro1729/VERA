# Architecture Decisions — Generic Resource Entitlement Engine

## 1. Goal

Build a minimal, highly generic resource-management and entitlement engine that can model a company, university, research lab, hospital, NGO, government organization, or another hierarchical institution without adding domain-specific Java classes.

V1 deliberately uses:

- Spring Boot HTTP/JSON boundary.
- OOP domain model in Java.
- In-memory state only.
- No database.
- No UI.
- No authentication/authorization layer yet.
- No background scheduler.
- One simple conflict rule: **nearest entitlement wins**.

The design should stay small now while leaving clear extension points for persistence, authentication, richer policies, audit logs, and distributed usage accounting later.

---

## 2. Core mental model

```text
Tenant
└── Root Scope
    ├── Scope
    │   ├── Scope
    │   │   └── Subject
    │   └── Subject
    └── Scope
        └── Subject

Tenant also owns a catalog of Resource definitions.

Scope OR Subject
      │
      └──── EntitlementGrant ──── Resource + EntitlementValue
                                    │
                                    └──── optional shared Usage pool
```

The platform understands only a few universal concepts:

- **Tenant**: isolated customer/world.
- **Scope**: recursive organizational grouping.
- **Subject**: individual actor/entity that can resolve entitlements and use resources.
- **Resource**: something whose access/capacity/features are managed.
- **EntitlementDefinition**: declares a grantable property on a resource.
- **EntitlementGrant**: assigns one entitlement value to one Scope or Subject.
- **Usage**: current consumption for a consumable grant.

It does **not** understand `Department`, `Team`, `Student`, `Employee`, `Lab`, `Professor`, etc. Those are tenant-defined `kind` strings.

---

## 3. Why `Tenant`, not `Organization`

`Organization` suggests a company-like structure. `Tenant` is intentionally neutral.

Examples:

```text
Tenant: Acme Corporation
Scope kinds: company -> department -> team
Subject kinds: employee, contractor
```

```text
Tenant: Example University
Scope kinds: university -> school -> department -> research_lab
Subject kinds: professor, phd_student
```

```text
Tenant: Research Institute
Scope kinds: institute -> division -> project -> experiment
Subject kinds: researcher, service_account
```

All use the same Java classes.

---

## 4. Scope is recursive and tenant-defined

`Scope` contains hierarchy information and references subjects.

Important fields:

```java
class Scope {
    String id;
    String kind;
    String name;
    Map<String, Object> metadata;
    String parentScopeId;
    List<String> childScopeIds;
    List<String> subjectIds;
}
```

`kind` is a string rather than an enum because the tenant defines its vocabulary.

Do not create Java classes such as:

```text
Department
Team
University
School
ResearchLab
BusinessUnit
```

They are all scopes.

---

## 5. Subject is also tenant-defined

A Subject is an individual entity capable of receiving an override and/or using a resource.

```java
class Subject {
    String id;
    String kind;
    String name;
    Map<String, Object> metadata;
    String scopeId;
}
```

Possible tenant-defined subject kinds:

```text
employee
student
professor
researcher
contractor
service_account
AI_agent
application
device
bot
```

V1 gives one subject one current scope. Multiple memberships can be added later if a real use case requires conflict semantics between multiple branches.

---

## 6. Resources are catalog entries; grants belong to scopes/subjects

A Resource exists in a tenant's catalog, but simply existing does not give anyone access.

```java
record Resource(
    String id,
    String kind,
    String name,
    Map<String, Object> metadata,
    Map<String, EntitlementValue> properties,
    List<EntitlementDefinition> entitlementDefinitions
) {}
```

Examples:

```text
OpenAI API
GPU Cluster
Storage
GitHub
Database
Dataset
Software License
Research Instrument
Cloud Account
Building
Internal Service
```

### Resource properties vs entitlements

This distinction is important.

**Resource property** = fact/capacity about the resource itself.

```text
GPU Cluster.capacity = 100 GPU
API.totalCapacity = 10,000,000 requests/month
```

**Entitlement grant** = what a specific Scope or Subject may use.

```text
Engineering -> GPU Cluster -> maxCount = 60 GPU
ML -> API -> requests = 5,000,000/month
Alice -> API -> requests = 1,000,000/month
```

Resource properties use the same generic `EntitlementValue` family because capacities can naturally be boolean, numeric, quota-like, time-bound, etc.

V1 does not enforce that all grants sum to a resource's physical capacity. That is intentionally deferred because allocation semantics (overcommit, reservations, borrowing, weighted sharing) differ by domain.

---

## 7. Entitlement definitions belong to resources

An entitlement definition describes what can be granted for a particular resource.

```java
record EntitlementDefinition(
    String key,
    String name,
    EntitlementValueType valueType
) {}
```

Example for an API resource:

```text
api.enabled       -> BOOLEAN
api.requests      -> QUOTA
api.maxBatch      -> QUANTITY
api.temperature   -> RANGE
api.models        -> SET
api.tier          -> TEXT
api.accessWindow  -> TIME_RANGE
```

This prevents arbitrary incompatible values from being assigned. A grant's value type must match the resource's definition.

---

## 8. Generic entitlement values

V1 includes seven value types.

### BOOLEAN

```java
record BooleanValue(boolean value)
```

Use cases:

```text
canUseGPU = true
api.enabled = false
canDownload = true
```

### QUANTITY

```java
record QuantityValue(BigDecimal value, String unit)
```

Use cases:

```text
maxGPUs = 8 gpu
storage = 500 GB
maxProjects = 20 project
maxTokens = 128000 token
budget = 10000 USD
```

It is a hard value/limit, not a continuously consumed periodic pool.

### QUOTA

```java
record QuotaValue(BigDecimal limit, String unit, QuotaPeriod period)
```

Supported V1 periods:

```text
DAILY
WEEKLY
MONTHLY
YEARLY
```

Use cases:

```text
100,000 requests/month
500 GPU-hours/month
5 TB/year
100 exports/day
```

Quota is the V1 consumable entitlement type.

### RANGE

```java
record RangeValue(BigDecimal min, BigDecimal max, String unit)
```

Use cases:

```text
temperature = 0..2
GPU count = 1..8
transaction amount = 0..10000 USD
```

### TIME_RANGE

```java
record TimeRangeValue(Instant from, Instant until)
```

Use cases:

```text
contractor access
intern access
research grant validity
temporary admin access
trial period
```

No background job is needed. Access is computed from the current clock when evaluated.

### SET

```java
record SetValue(Set<String> values)
```

Use cases:

```text
allowedModels = [small, large]
allowedRegions = [india, singapore]
allowedActions = [read, write]
```

### TEXT

```java
record TextValue(String value)
```

Use cases:

```text
supportTier = premium
computeClass = research
dataResidency = india
licenseType = academic
```

---

## 9. EntitlementGrant is the central assignment primitive

```java
record EntitlementGrant(
    String id,
    Target target,
    String resourceId,
    String entitlementKey,
    EntitlementValue value
) {}
```

`Target` is one of:

```java
enum TargetType {
    SCOPE,
    SUBJECT
}
```

There is intentionally no `TENANT` target.

A company-wide default is assigned to the **root scope**, which means the normal inheritance algorithm handles it without a special case.

Example:

```text
Target: Scope(engineering)
Resource: api
Entitlement: api.requests
Value: 1,000,000 requests/month
```

or:

```text
Target: Subject(alice)
Resource: api
Entitlement: api.requests
Value: 2,000,000 requests/month
```

There are no separate classes such as `DepartmentQuota`, `TeamQuota`, `UserQuota`, or `CompanyQuota`.

---

## 10. Conflict resolution: nearest entitlement wins

V1 has one rule:

> Resolve from the subject outward. The first matching entitlement wins.

Resolution order:

```text
1. Subject-specific grant
2. Subject's current Scope
3. Parent Scope
4. Parent's parent
5. ...
6. Root Scope
```

Example:

```text
Root                 API = 100K/month
└── Engineering      API = 1M/month
    └── ML            API = 5M/month
        └── Alice     API = 10M/month
```

Alice resolves to `10M/month`.

If Alice's grant is removed, she resolves to ML's `5M/month`.

If ML's grant is removed, she resolves to Engineering's `1M/month`.

This is intentionally simpler than additive/min/max/deny-wins strategies. Those can be added later behind a resolution-strategy abstraction only when required.

---

## 11. Shared scope usage is a first-class behavior

Usage belongs to the **winning grant**, not to the person making the request.

Example:

```text
Engineering grant G1:
    API = 1,000,000 requests/month

Alice consumes 100
Bob consumes 250
```

Both resolve to `G1`, therefore:

```text
G1 consumed = 350
G1 remaining = 999,650
```

This makes a scope entitlement a real collective pool.

If Alice receives a personal quota grant, Alice begins consuming from her own grant while Bob continues consuming from Engineering's pool.

Core invariant:

```text
resolved entitlement
      -> winning grant
      -> usage pool keyed by grantId
```

---

## 12. Usage and time handling

```java
class Usage {
    String grantId;
    BigDecimal consumed;
    Instant periodStart;
    Instant periodEnd;
}
```

Usage is stored in memory by `UsageStore`.

### No scheduler for quota resets

Quota periods use calendar UTC boundaries:

- DAILY: UTC day
- WEEKLY: Monday 00:00 UTC to next Monday
- MONTHLY: first day of month to first day of next month
- YEARLY: Jan 1 to Jan 1 of next year

On evaluation/consumption, the application calculates the current quota window. If the stored usage belongs to an older window, it lazily resets the counter to zero.

This means no cron job/background thread is required in V1.

### No scheduler for time-range entitlements

A `TIME_RANGE` grant is evaluated using an injected Java `Clock`:

```text
from <= now < until
```

Tests use a mutable/fixed clock, so time behavior is deterministic.

---

## 13. Registration flow

A tenant sends one registration JSON containing:

```text
tenant identity
nested scope structure
subjects inside scopes
resource catalog
resource properties
resource entitlement definitions
initial grants
```

Flow:

```text
POST /api/tenants/register
        |
        v
RegistrationRequest
        |
        v
RegistrationService
        |
        +-- recursively builds Scope objects
        +-- builds Subject objects
        +-- indexes scopes/subjects by id
        +-- validates Resources
        +-- validates initial grants
        v
Tenant
        |
        v
TenantRegistry (in memory)
```

Internally the nested input is flattened into indexed OOP objects while retaining parent/child references.

This gives both:

- hierarchy traversal; and
- fast `id -> object` lookup for commands.

---

## 14. Runtime JSON commands

The company does not need to resend the complete structure after registration.

It sends commands:

```text
POST /api/commands
```

Envelope:

```json
{
  "type": "MOVE_SUBJECT",
  "tenantId": "acme",
  "payload": {
    "subjectId": "alice",
    "newScopeId": "ml"
  }
}
```

Supported V1 commands:

```text
ADD_SCOPE
UPDATE_SCOPE
REMOVE_SCOPE
MOVE_SCOPE

ADD_SUBJECT
UPDATE_SUBJECT
REMOVE_SUBJECT
MOVE_SUBJECT

ADD_RESOURCE
UPDATE_RESOURCE
REMOVE_RESOURCE

SET_ENTITLEMENT
REMOVE_ENTITLEMENT
```

`SET_ENTITLEMENT` is create-or-replace for the same:

```text
target + resource + entitlementKey
```

When a grant is replaced or removed, its old usage pool is removed as well.

Moving a subject changes inherited resolution immediately; no grants need to be copied.

---

## 15. Evaluation API

```text
POST /api/entitlements/evaluate
```

Example:

```json
{
  "tenantId": "acme",
  "subjectId": "alice",
  "resourceId": "api",
  "entitlementKey": "api.maxBatch",
  "requestedValue": 50
}
```

The service:

1. resolves the nearest grant;
2. applies semantics based on value type;
3. returns the source target and grant id.

Evaluation semantics in V1:

```text
BOOLEAN     -> value must allow access
QUANTITY    -> requested <= limit
QUOTA       -> requested <= remaining pool
RANGE       -> min <= requested <= max
TIME_RANGE  -> current clock is inside range
SET         -> requested member/subset must be allowed
TEXT        -> requested string must match
```

---

## 16. Consumption API

```text
POST /api/entitlements/consume
```

Example:

```json
{
  "tenantId": "acme",
  "subjectId": "alice",
  "resourceId": "api",
  "entitlementKey": "api.requests",
  "amount": 400
}
```

V1 permits consumption only for `QUOTA` values.

Flow:

```text
Subject
  |
  v
EntitlementResolver
  |
  v
winning grant
  |
  v
UsageStore[grantId]
  |
  +-- ensure current quota window
  +-- check remaining
  +-- increment if allowed
  v
ConsumptionResult
```

A rejected over-limit request does not increment usage.

---

## 17. Main classes

### Domain

```text
Tenant
Scope
Subject
Resource
EntitlementDefinition
Target
TargetType
EntitlementGrant
ResolvedEntitlement
Usage

EntitlementValue
  |- BooleanValue
  |- QuantityValue
  |- QuotaValue
  |- RangeValue
  |- TimeRangeValue
  |- SetValue
  `- TextValue

EntitlementValueType
QuotaPeriod
```

### Request/API DTOs

```text
RegistrationRequest
TenantInput
ScopeInput
SubjectInput
GrantInput

CommandRequest
CommandType
CommandPayloads

EvaluationRequest
EvaluationResult
ConsumptionRequest
ConsumptionResult
CommandResult
```

### Services

```text
RegistrationService
CommandService
EntitlementResolver
EntitlementService
UsageService
```

### In-memory stores

```text
TenantRegistry
UsageStore
```

### HTTP

```text
TenantController
CommandController
EntitlementController
ApiExceptionHandler
```

---

## 18. In-memory storage decision

V1 deliberately stores all state in Java memory:

```text
TenantRegistry -> ConcurrentHashMap<tenantId, Tenant>
UsageStore     -> ConcurrentHashMap<grantId, Usage>
```

Consequences:

- restart loses all state;
- multiple application replicas would not share state;
- this is not production persistence;
- it is ideal for validating the domain model and API behavior first.

Later these stores can be replaced by repository interfaces/database implementations without changing the entitlement concepts.

---

## 19. Concurrency decision

The registry/store maps are concurrent. Mutating commands and usage consumption synchronize on the in-memory Tenant object to avoid simple race conditions inside one JVM.

This is deliberately only a V1 single-process consistency mechanism.

A distributed deployment later needs persistent transactions/atomic counters/locking or a specialized usage system.

---

## 20. Intentionally not implemented yet

Do not add these until actual requirements justify them:

```text
Database
UI
Authentication
Spring Security
RBAC roles
Kafka/events
Redis
Billing
Audit history
Approval workflow
Multi-scope subject memberships
Additive/min/max entitlement resolution
Resource-capacity allocation enforcement
Reservations
Distributed quota counters
Idempotency keys
Versioned configuration
Background jobs
```

The goal of this version is to validate the reusable domain abstraction first.

---

## 21. Natural future extensions

When the core proves useful, likely extensions are:

1. Persistence repositories for Tenant/configuration and usage.
2. Authentication and tenant isolation at the API boundary.
3. Audit log for every configuration command and consumption event.
4. Idempotency keys for consumption requests.
5. Optimistic versioning for configuration changes.
6. Resource-capacity validation/allocation policies.
7. Multiple subject memberships with explicit conflict semantics.
8. Additional resolution strategies such as `MIN`, `MAX`, `ADD`, `UNION`, `INTERSECTION`, `DENY_WINS`.
9. Custom quota windows/time zones.
10. Reservations and release operations for concurrent resources.
11. Event streaming for usage/changes.
12. Persistence-backed distributed counters.

None of these require replacing the core concepts of Tenant, Scope, Subject, Resource, EntitlementGrant, and Usage.

---

## 22. Application invariant

The project should preserve this sentence as the architectural center:

> A Tenant defines a hierarchical collection of Scopes containing Subjects. The Tenant catalogs Resources and the entitlement types each Resource supports. Typed Entitlement Grants can be assigned to a Scope or Subject. When multiple assignments exist, the nearest assignment in the hierarchy wins. Consumable entitlements belong to the winning grant, so every Subject resolving to the same Scope grant consumes from the same shared usage pool.

That is the V1 product model.
