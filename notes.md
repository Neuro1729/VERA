# Complete Explanation of the Resource Entitlement Engine

## 1. What the application is

Our application is a generic hierarchical resource-management and entitlement system.

The important idea is that the application does **not** understand concepts such as:

* Company
* Department
* Team
* University
* School
* Research lab
* Employee
* Professor
* Student

Instead, it understands a few generic concepts:

```text
Tenant
Scope
Subject
Resource
Entitlement
Grant
Usage
```

A company can therefore model:

```text
Acme
└── Engineering
    ├── Backend
    │   ├── Alice
    │   └── Bob
    └── ML
        └── Charlie
```

while a university can model:

```text
University
└── School of Engineering
    └── Computer Science
        └── AI Lab
            ├── Professor A
            └── Student B
```

using exactly the same Java classes.

The complete runtime idea is:

```text
JSON Request
     ↓
Spring Controller
     ↓
Service
     ↓
Tenant + Scope + Subject + Resource objects
     ↓
EntitlementResolver
     ↓
Winning EntitlementGrant
     ↓
Evaluation / Consumption
     ↓
JSON Response
```

The project currently contains **49 production Java source files**, plus test classes.

---

# 2. `ResourceEntitlementApplication`

This is the Spring Boot application entry point.

```java
@SpringBootApplication
public class ResourceEntitlementApplication {
    public static void main(String[] args) {
        SpringApplication.run(
            ResourceEntitlementApplication.class,
            args
        );
    }
}
```

Its only responsibility is to start Spring Boot.

Once started, Spring creates objects such as:

```text
TenantController
CommandController
EntitlementController

RegistrationService
CommandService
EntitlementService
EntitlementResolver
UsageService

TenantRegistry
UsageStore
```

and connects them using dependency injection.

---

# DOMAIN CLASSES

These classes represent the actual business model.

---

# 3. `Tenant`

`Tenant` represents one customer using our platform.

Examples:

```text
Acme Corporation

Example University

XYZ Research Institute
```

The class contains roughly:

```java
class Tenant {

    String id;
    String name;

    String rootScopeId;

    Map<String, Scope> scopes;

    Map<String, Subject> subjects;

    Map<String, Resource> resources;

    Map<String, EntitlementGrant> grants;
}
```

For example:

```text
Tenant:
    id = acme
    name = Acme Corporation
```

Its maps might contain:

```text
scopes:
    root
    engineering
    backend
    ml

subjects:
    alice
    bob
    charlie

resources:
    openai-api
    gpu-main

grants:
    grant-eng-api
    grant-ml-gpu
```

## Why these maps exist

The actual organization is hierarchical, but commands usually reference objects by ID.

For example:

```json
{
    "subjectId": "alice"
}
```

Instead of recursively searching:

```text
root
 ↓
Engineering
 ↓
Backend
 ↓
Alice
```

we can simply do:

```java
tenant.getSubjects().get("alice");
```

The hierarchy tells us relationships.

The maps give us fast object lookup.

---

# 4. `Scope`

`Scope` represents a node in the tenant's hierarchy.

It can mean anything.

For a company:

```text
company
division
department
team
project
```

For a university:

```text
university
school
department
lab
course
```

For a research institute:

```text
institute
division
research_group
experiment
```

The class contains:

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

Example:

```text
id = engineering
kind = department
name = Engineering
```

Its children might be:

```text
childScopeIds:
    backend
    ml
```

and subjects might be:

```text
subjectIds:
    manager-1
```

## Why `kind` is a String

We intentionally do not have:

```java
enum ScopeType {
    DEPARTMENT,
    TEAM,
    LAB
}
```

because that would force our assumptions on customers.

Instead Acme can define:

```text
kind = team
```

while a university can define:

```text
kind = research_lab
```

without changing our Java code.

## Scope mutation methods

`Scope` contains methods such as:

```text
setKind()
setName()
mergeMetadata()

setParentScopeId()

addChild()
removeChild()

addSubject()
removeSubject()
```

These are used by `CommandService` when the company's structure changes.

---

# 5. `Subject`

A `Subject` represents an individual actor/entity inside the hierarchy.

A subject might be:

```text
employee
student
professor
contractor
researcher

service_account
AI_agent
application
device
bot
```

The class contains:

```java
class Subject {

    String id;

    String kind;

    String name;

    Map<String, Object> metadata;

    String scopeId;
}
```

Example:

```text
id = alice
kind = employee
name = Alice
scopeId = backend
```

So:

```text
Alice
   ↓ scopeId
Backend
   ↓ parentScopeId
Engineering
   ↓
Root
```

This chain becomes very important for entitlement inheritance.

Like `Scope`, `Subject` has methods for changing:

```text
kind
name
metadata
scopeId
```

---

# 6. `Resource`

`Resource` represents something the tenant has and wants to manage.

Examples:

```text
OpenAI API

GPU Cluster

Storage

GitHub

Database

Cloud Account

Research Dataset

Software License

Laboratory Equipment
```

The class is:

```java
record Resource(
    String id,
    String kind,
    String name,

    Map<String, Object> metadata,

    Map<String, EntitlementValue> properties,

    List<EntitlementDefinition> entitlementDefinitions
)
```

Example:

```text
Resource:
    id = gpu-main
    kind = compute
    name = Main GPU Cluster
```

## Resource properties

Properties describe the resource itself.

For example:

```text
capacity = 100 GPUs
```

or:

```text
totalStorage = 10 TB
```

This is different from saying:

```text
Engineering gets 20 GPUs.
```

The first describes the resource.

The second is an entitlement.

So:

```text
Resource property
    = what exists

EntitlementGrant
    = what somebody gets
```

---

# 7. `EntitlementDefinition`

A resource can have multiple independently manageable properties.

For example:

```text
Resource: GPU Cluster

gpu.enabled
gpu.hours
gpu.maxConcurrent
gpu.allowedTypes
gpu.accessWindow
```

`EntitlementDefinition` defines one of these properties.

```java
record EntitlementDefinition(
    String key,
    String name,
    EntitlementValueType valueType
)
```

Example:

```text
key = gpu.hours
name = GPU Hours
valueType = QUOTA
```

Another:

```text
key = gpu.enabled
name = GPU Access
valueType = BOOLEAN
```

## Why the entitlement key matters

The resource identifies:

> What thing are we managing?

For example:

```text
gpu-main
```

The entitlement key identifies:

> Which configurable property of that resource?

For example:

```text
gpu.hours
```

Therefore:

```text
resourceId = gpu-main

entitlementKey = gpu.hours
```

means:

> We are talking about the GPU-hour allocation on the main GPU cluster.

---

# 8. `EntitlementValue`

`EntitlementValue` is the common parent of all entitlement value types.

```java
sealed interface EntitlementValue
```

Supported types are:

```text
BooleanValue
QuantityValue
QuotaValue
RangeValue
TimeRangeValue
SetValue
TextValue
```

Jackson also uses this hierarchy when converting JSON.

For example:

```json
{
    "type": "QUOTA",
    "limit": 5000,
    "unit": "gpu-hour",
    "period": "MONTHLY"
}
```

automatically becomes:

```java
QuotaValue
```

---

# 9. `EntitlementValueType`

This enum describes which entitlement value type a definition expects.

```java
enum EntitlementValueType {
    BOOLEAN,
    QUANTITY,
    QUOTA,
    RANGE,
    TIME_RANGE,
    SET,
    TEXT
}
```

For example:

```text
gpu.enabled
    → BOOLEAN

gpu.hours
    → QUOTA

gpu.maxConcurrent
    → QUANTITY
```

This enables validation.

If:

```text
gpu.hours requires QUOTA
```

but somebody sends:

```text
BooleanValue(true)
```

the application rejects it.

---

# 10. `BooleanValue`

Represents true/false entitlement values.

```java
record BooleanValue(
    boolean value
)
```

Examples:

```text
gpu.enabled = true

download.allowed = false

github.copilot = true
```

There is no usage counter for a boolean.

It is simply evaluated.

---

# 11. `QuantityValue`

Represents a hard numeric amount or upper limit.

```java
record QuantityValue(
    BigDecimal value,
    String unit
)
```

Examples:

```text
8 gpu

500 GB

20 projects

128000 tokens
```

For example:

```text
gpu.maxConcurrent = 8 GPU
```

If someone requests:

```text
6
```

then:

```text
6 <= 8
```

so it is allowed.

If they request:

```text
12
```

it is rejected.

Unlike a quota, it does not continuously decrease.

---

# 12. `QuotaValue`

A `QuotaValue` represents a consumable periodic allocation.

```java
record QuotaValue(
    BigDecimal limit,
    String unit,
    QuotaPeriod period
)
```

Examples:

```text
100,000 API requests / month

500 GPU-hours / month

5 TB / year

100 exports / day
```

This is currently the main entitlement type connected to `Usage`.

---

# 13. `QuotaPeriod`

The supported quota reset periods are:

```java
enum QuotaPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
```

For example:

```text
limit = 1,000,000
unit = request
period = MONTHLY
```

means:

```text
1,000,000 requests every calendar month.
```

---

# 14. `RangeValue`

Represents an allowed numeric range.

```java
record RangeValue(
    BigDecimal min,
    BigDecimal max,
    String unit
)
```

Examples:

```text
GPU count:
1 → 8

Transaction amount:
0 → 10000 USD
```

The evaluator checks:

```text
min <= requested <= max
```

---

# 15. `TimeRangeValue`

Represents access that only exists during a certain period.

```java
record TimeRangeValue(
    Instant from,
    Instant until
)
```

Examples:

```text
Contractor access:
August 1 → December 31

Research grant:
January 1 → June 30

Trial:
August 10 → August 20
```

The application does not continuously update this entitlement.

Instead it checks the current time whenever an evaluation occurs.

---

# 16. `SetValue`

Represents a collection of allowed values.

```java
record SetValue(
    Set<String> values
)
```

Examples:

```text
allowedModels:
    gpt-x
    llama-x

allowedRegions:
    india
    singapore

allowedActions:
    read
    write
```

The application can check whether one requested value or several requested values belong to the allowed set.

---

# 17. `TextValue`

Represents a text configuration.

```java
record TextValue(
    String value
)
```

Examples:

```text
supportTier = premium

computeClass = research

licenseType = academic
```

Evaluation checks whether the requested string matches the entitlement value.

---

# 18. `TargetType`

A grant must belong to either:

```java
enum TargetType {
    SCOPE,
    SUBJECT
}
```

Those are intentionally the only two types.

We don't have:

```text
TEAM
DEPARTMENT
UNIVERSITY
EMPLOYEE
```

because teams and departments are scopes, while employees and students are subjects.

---

# 19. `Target`

`Target` identifies who owns an entitlement grant.

```java
record Target(
    TargetType type,
    String id
)
```

Example:

```text
type = SCOPE
id = engineering
```

means:

> This grant belongs to the Engineering scope.

Another:

```text
type = SUBJECT
id = alice
```

means:

> This grant belongs directly to Alice.

The target itself is only a reference.

It doesn't store the whole Engineering object.

---

# 20. `EntitlementGrant`

This is one of the central classes in the whole project.

```java
record EntitlementGrant(
    String id,
    Target target,
    String resourceId,
    String entitlementKey,
    EntitlementValue value
)
```

A grant means:

> Give one entitlement value for one resource property to one scope or subject.

Example:

```text
Grant ID:
    grant-eng-gpu-hours

Target:
    Engineering

Resource:
    gpu-main

Entitlement Key:
    gpu.hours

Value:
    5000 GPU-hours/month
```

## One grant = one entitlement key

If Engineering gets:

```text
gpu.enabled = true
gpu.hours = 5000/month
gpu.maxConcurrent = 8
```

those are three grants.

```text
Grant A
    gpu.enabled = true

Grant B
    gpu.hours = 5000/month

Grant C
    gpu.maxConcurrent = 8
```

This allows every property to inherit independently.

## Why the grant has an ID

The grant itself represents a specific allocation.

For example:

```text
Engineering's GPU-hour allocation
```

is different from:

```text
ML's GPU-hour allocation
```

even though both use the same:

```text
resourceId = gpu-main
entitlementKey = gpu.hours
```

The grant ID also identifies the usage pool.

```text
grant-eng-gpu-hours
        ↓
Usage
        ↓
1500 consumed
```

---

# 21. `ResolvedEntitlement`

This is a small wrapper around the grant that won inheritance resolution.

```java
record ResolvedEntitlement(
    EntitlementGrant grant
)
```

It also provides:

```java
source()
```

which returns the grant's target.

For example:

```text
Alice asks for gpu.hours

Resolver determines:
Engineering's grant wins
```

Then:

```text
ResolvedEntitlement

grant:
    grant-eng-gpu-hours

source:
    SCOPE:engineering
```

---

# 22. `Usage`

`Usage` tracks how much of a consumable quota grant has been consumed.

```java
class Usage {

    String grantId;

    BigDecimal consumed;

    Instant periodStart;

    Instant periodEnd;
}
```

Example:

```text
grantId = grant-eng-api

consumed = 350000

periodStart = August 1
periodEnd = September 1
```

The class contains:

```text
add(amount)

reset(start, end)
```

## Why usage belongs to the grant

Suppose:

```text
Engineering:
    API requests = 1M/month

Alice
Bob
```

Alice consumes:

```text
100K
```

Bob consumes:

```text
200K
```

Both resolve to the same Engineering grant.

Therefore:

```text
Engineering grant usage:

100K + 200K = 300K
```

They share one quota.

That is exactly the collective-scope behavior we wanted.

---

# REGISTRATION REQUEST CLASSES

These classes represent the JSON used when a customer initially registers.

---

# 23. `TenantInput`

Very small registration DTO:

```java
record TenantInput(
    String id,
    String name
)
```

Example:

```json
{
    "id": "acme",
    "name": "Acme Corporation"
}
```

---

# 24. `SubjectInput`

Represents a subject inside registration JSON.

```java
record SubjectInput(
    String id,
    String kind,
    String name,
    Map<String, Object> metadata
)
```

It does not need `scopeId` because the subject is nested inside its scope in the registration JSON.

`RegistrationService` determines the `scopeId` while building the structure.

---

# 25. `ScopeInput`

Represents the recursive JSON structure.

```java
record ScopeInput(
    String id,
    String kind,
    String name,
    Map<String, Object> metadata,
    List<ScopeInput> children,
    List<SubjectInput> subjects
)
```

The important part is:

```text
List<ScopeInput> children
```

because this allows arbitrary hierarchy depth.

```text
Company
└── Division
    └── Department
        └── Team
            └── Project
                └── ...
```

No maximum depth is hard-coded.

---

# 26. `GrantInput`

Represents a grant included during initial tenant registration.

```java
record GrantInput(
    String id,
    Target target,
    String resourceId,
    String entitlementKey,
    EntitlementValue value
)
```

If the company does not provide a grant ID, `RegistrationService` generates a UUID.

---

# 27. `RegistrationRequest`

This represents the complete initial registration JSON.

```java
record RegistrationRequest(
    TenantInput tenant,
    ScopeInput structure,
    List<Resource> resources,
    List<GrantInput> grants
)
```

So one request describes:

```text
Tenant identity
+
organizational structure
+
subjects
+
resource catalog
+
initial entitlement grants
```

---

# COMMAND CLASSES

After registration, the company does not need to resend everything.

It sends commands to change the existing in-memory model.

---

# 28. `CommandType`

Defines the supported mutations.

```java
enum CommandType {
    ADD_SCOPE,
    UPDATE_SCOPE,
    REMOVE_SCOPE,
    MOVE_SCOPE,

    ADD_SUBJECT,
    UPDATE_SUBJECT,
    REMOVE_SUBJECT,
    MOVE_SUBJECT,

    ADD_RESOURCE,
    UPDATE_RESOURCE,
    REMOVE_RESOURCE,

    SET_ENTITLEMENT,
    REMOVE_ENTITLEMENT
}
```

These commands allow almost the entire tenant configuration to change dynamically.

---

# 29. `CommandRequest`

A generic command envelope.

```java
record CommandRequest(
    CommandType type,
    String tenantId,
    JsonNode payload
)
```

Example:

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

`payload` starts as generic JSON.

`CommandService` converts it to the correct strongly typed command payload.

---

# 30. `CommandPayloads`

`CommandPayloads` is a container class holding small records for every supported command.

It is not instantiated itself.

It contains the following records.

## `ScopeData`

Basic information required when creating a scope.

```text
id
kind
name
metadata
```

## `AddScope`

Contains:

```text
parentScopeId
scope
```

Meaning:

> Add this new scope under this parent.

## `UpdateScope`

Contains:

```text
scopeId
kind
name
metadata
```

## `RemoveScope`

Contains:

```text
scopeId
```

## `MoveScope`

Contains:

```text
scopeId
newParentScopeId
```

---

## `AddSubject`

Contains:

```text
scopeId
SubjectInput subject
```

## `UpdateSubject`

Contains:

```text
subjectId
kind
name
metadata
```

## `RemoveSubject`

Contains:

```text
subjectId
```

## `MoveSubject`

Contains:

```text
subjectId
newScopeId
```

---

## `AddResource`

Contains a new:

```text
Resource
```

## `UpdateResource`

Can change:

```text
kind
name
metadata
properties
entitlementDefinitions
```

## `RemoveResource`

Contains:

```text
resourceId
```

---

## `SetEntitlement`

Contains:

```text
grantId
target
resourceId
entitlementKey
value
```

It behaves like create-or-replace for:

```text
target
+
resource
+
entitlement key
```

## `RemoveEntitlement`

Contains:

```text
target
resourceId
entitlementKey
```

This lets the company remove an override.

For example:

```text
ML has gpu.hours = 5000/month
```

If that grant is removed, subjects in ML may automatically fall back to:

```text
Engineering's gpu.hours
```

because of inheritance.

---

# 31. `CommandResult`

Simple response from a command.

```java
record CommandResult(
    boolean success,
    String message
)
```

Example:

```json
{
    "success": true,
    "message": "subject moved: alice"
}
```

---

# EVALUATION CLASSES

---

# 32. `EvaluationRequest`

Used when a subject wants to know:

> Am I allowed to do this?

```java
record EvaluationRequest(
    String tenantId,
    String subjectId,
    String resourceId,
    String entitlementKey,
    JsonNode requestedValue
)
```

Example:

```text
Alice wants 6 GPUs.
```

Request:

```text
tenant = acme
subject = alice
resource = gpu-main
entitlement = gpu.maxConcurrent
requestedValue = 6
```

---

# 33. `EvaluationResult`

Response from entitlement evaluation.

```java
record EvaluationResult(
    boolean allowed,
    String reason,

    String grantId,

    Target source,

    EntitlementValue value,

    BigDecimal remaining
)
```

It tells the caller not just:

```text
yes/no
```

but also:

```text
which grant won
where the grant came from
what entitlement value applies
how much quota remains if relevant
```

---

# CONSUMPTION CLASSES

---

# 34. `ConsumptionRequest`

Represents an actual attempt to consume a quota.

```java
record ConsumptionRequest(
    String tenantId,
    String subjectId,
    String resourceId,
    String entitlementKey,
    BigDecimal amount
)
```

Example:

```json
{
    "tenantId": "acme",
    "subjectId": "alice",
    "resourceId": "openai-api",
    "entitlementKey": "api.requests",
    "amount": 500
}
```

This means:

> Alice has actually used 500 API requests.

Alice does not specify which department pool should be used.

The resolver determines that automatically.

---

# 35. `ConsumptionResult`

Returned after a consumption attempt.

```java
record ConsumptionResult(
    boolean allowed,
    String reason,

    String grantId,

    Target source,

    BigDecimal requested,

    BigDecimal consumed,

    BigDecimal limit,

    BigDecimal remaining,

    Instant periodStart,

    Instant periodEnd
)
```

Example response:

```text
allowed = true

grantId = grant-eng-api

source = Engineering

requested = 500

consumed = 3500

limit = 10000

remaining = 6500
```

This makes quota behavior transparent to the client.

---

# SERVICES

Services contain our business logic.

---

# 36. `RegistrationService`

Responsible for converting the initial registration JSON into our OOP model.

Flow:

```text
RegistrationRequest
        ↓
RegistrationService
        ↓
create Tenant
        ↓
build Scope tree recursively
        ↓
create Subjects
        ↓
index everything in Tenant maps
        ↓
register Resources
        ↓
validate Grants
        ↓
TenantRegistry
```

The important method is conceptually:

```text
buildScopeTree()
```

which recursively turns:

```text
nested ScopeInput
```

into:

```text
Scope objects
+
parentScopeId
+
childScopeIds
+
Subject objects
```

The service also checks for things such as:

```text
duplicate scope IDs
duplicate subject IDs
duplicate resource IDs
duplicate grant IDs
```

---

# 37. `ModelValidation`

This is an internal helper responsible for validating the domain model.

It is package-private because it is used internally by services.

Its important responsibilities are:

## Validate resources

Ensures one resource does not contain duplicate entitlement keys.

For example this is invalid:

```text
gpu.hours
gpu.hours
```

## Validate grants

Checks:

```text
Does the target scope/subject exist?

Does the resource exist?

Does the entitlement key exist on that resource?

Does the value type match the definition?
```

For example:

```text
gpu.hours expects QUOTA

but grant value is BOOLEAN
```

is rejected.

## Find exact grants

It contains:

```java
findExactGrant(
    tenant,
    target,
    resourceId,
    entitlementKey
)
```

This searches for exactly:

```text
Target
+
Resource
+
Entitlement Key
```

and is heavily used by entitlement resolution.

---

# 38. `EntitlementResolver`

This is the inheritance/search engine.

Its responsibility is only:

> Given a subject, resource and entitlement key, which grant applies?

It does not update usage.

It does not calculate business limits.

It only finds the winning grant.

Algorithm:

```text
1. Check Subject

2. Check Subject's Scope

3. Check parent Scope

4. Continue toward root

5. First matching grant wins
```

Example:

```text
Root
    gpu.hours = 1000

└── Engineering
    gpu.hours = 5000

    └── ML

        └── Alice
```

Alice asks for:

```text
gpu.hours
```

Resolver searches:

```text
Alice
    none

ML
    none

Engineering
    FOUND 5000
```

It stops there.

Root's 1000 is ignored because Engineering is nearer.

This is our fundamental rule:

> **Nearest entitlement wins.**

---

# 39. `EntitlementService`

This service answers:

> Is this requested action/value allowed?

It first uses:

```text
EntitlementResolver
```

to get the winning grant.

Then it evaluates based on entitlement type.

## BOOLEAN

```text
true → allowed
false → denied
```

## QUANTITY

Checks:

```text
requested <= configured limit
```

## QUOTA

Gets remaining usage from `UsageService`.

Then:

```text
requested <= remaining
```

## RANGE

Checks:

```text
min <= requested <= max
```

## TIME_RANGE

Checks:

```text
from <= now < until
```

## SET

Checks that requested value or requested subset belongs to the allowed set.

## TEXT

Checks that the requested string equals the entitlement value.

So:

```text
EntitlementResolver
    → which grant?

EntitlementService
    → is the requested action valid under that grant?
```

---

# 40. `UsageService`

This service handles real quota consumption.

Its responsibility is:

> Resolve the correct quota pool, verify sufficient capacity remains, and increment usage if allowed.

Flow:

```text
ConsumptionRequest
        ↓
TenantRegistry
        ↓
EntitlementResolver
        ↓
winning Grant
        ↓
must be QUOTA
        ↓
get Usage for grant
        ↓
calculate remaining
        ↓
enough?
   /            \
 YES             NO
 ↓                ↓
increment       reject
 ↓
ConsumptionResult
```

Example:

```text
Engineering:
    API = 1000/month

Alice consumes 300

Bob consumes 400
```

Both resolve to the same Engineering grant.

Therefore:

```text
Usage[Engineering grant]

300 + 400 = 700
```

Remaining:

```text
1000 - 700 = 300
```

## `remaining()`

Returns remaining quota for a grant.

## `currentUsage()`

Gets or creates the current usage period.

If the quota period changed, it resets usage automatically.

## `removeUsage()`

Deletes usage associated with a grant.

This is useful when grants are removed or replaced.

---

# 41. `QuotaWindow`

A small internal record:

```java
record QuotaWindow(
    Instant start,
    Instant end
)
```

Its job is to calculate calendar quota periods.

For example:

### DAILY

```text
Aug 13 00:00 UTC
→
Aug 14 00:00 UTC
```

### WEEKLY

```text
Monday 00:00
→
next Monday 00:00
```

### MONTHLY

```text
Aug 1
→
Sep 1
```

### YEARLY

```text
Jan 1 2026
→
Jan 1 2027
```

`UsageService` uses this to implement lazy quota resets.

---

# 42. `CommandService`

This service handles runtime configuration changes.

It receives:

```text
CommandRequest
```

and switches based on:

```text
CommandType
```

It supports all structural and entitlement changes.

Examples:

```text
ADD_SCOPE

MOVE_SUBJECT

UPDATE_RESOURCE

SET_ENTITLEMENT
```

It synchronizes on the tenant object so two operations do not mutate the same tenant simultaneously inside the same JVM.

Important behaviors include:

## Adding scopes

Creates a scope and attaches it to its parent.

## Moving scopes

Updates:

```text
old parent
new parent
parentScopeId
```

It prevents cycles such as:

```text
A
└── B
    └── A
```

## Removing scopes

Removing a scope also removes its subtree:

```text
child scopes
subjects
grants belonging to them
usage belonging to those grants
```

## Moving subjects

Updates the subject's scope.

This automatically changes inherited entitlements.

No grants need to be copied.

## Managing resources

Can:

```text
add
update
remove
```

resources.

The service validates that resource updates do not make existing grants invalid.

## Managing entitlements

`SET_ENTITLEMENT` creates or replaces an entitlement for:

```text
target + resource + entitlementKey
```

`REMOVE_ENTITLEMENT` removes it.

---

# IN-MEMORY STORAGE

---

# 43. `TenantRegistry`

This is our current substitute for a database.

Internally:

```java
ConcurrentMap<String, Tenant> tenants
```

Example:

```text
acme
    → Tenant object

university-x
    → Tenant object
```

Important methods:

```text
register()

getRequired()

all()

clear()
```

For example:

```java
Tenant tenant =
    registry.getRequired("acme");
```

Right now restarting the application deletes everything because it only exists in RAM.

That is intentional for V1.

Later this abstraction can be replaced or wrapped with database repositories.

---

# 44. `UsageStore`

Stores the current `Usage` objects.

Currently conceptually:

```java
ConcurrentMap<String, Usage>
```

where the key is the grant ID.

Example:

```text
grant-eng-api
    →
Usage {
    consumed = 300000
}
```

Methods:

```text
get()
put()
remove()
all()
clear()
```

One improvement we already identified for later is using a composite key:

```text
tenantId + grantId
```

to make tenant isolation explicit.

---

# SPRING CONFIGURATION

---

# 45. `ClockConfig`

Provides a Spring `Clock` bean.

```java
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

Instead of business logic directly calling system time everywhere, services receive a `Clock`.

This makes time testable.

Production:

```text
real UTC clock
```

Tests:

```text
fake controllable clock
```

This is especially important for:

```text
monthly resets
yearly resets
time-range access
```

---

# HTTP CONTROLLERS

Controllers translate HTTP/JSON into service calls.

They should contain very little business logic.

---

# 46. `TenantController`

Handles tenant-related endpoints.

### Registration

```text
POST /api/tenants/register
```

Calls:

```text
RegistrationService
```

### Get one tenant

```text
GET /api/tenants/{tenantId}
```

### List tenants

```text
GET /api/tenants
```

### Inspect usage

```text
GET /api/tenants/{tenantId}/usage
```

The controller itself does not build structures or calculate entitlements.

It delegates those responsibilities.

---

# 47. `CommandController`

Endpoint:

```text
POST /api/commands
```

It receives:

```text
CommandRequest
```

and calls:

```text
CommandService.execute()
```

That's all.

---

# 48. `EntitlementController`

Provides two important endpoints.

## Evaluate

```text
POST /api/entitlements/evaluate
```

Calls:

```text
EntitlementService
```

This means:

> Check whether something is allowed without consuming anything.

## Consume

```text
POST /api/entitlements/consume
```

Calls:

```text
UsageService
```

This means:

> Actual usage occurred; validate it and increase the correct quota pool.

---

# ERROR HANDLING

---

# 49. `ApiError`

Standard error response.

```java
record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message
)
```

Example:

```json
{
    "status": 404,
    "error": "Not Found",
    "message": "subject not found: alice"
}
```

---

# 50. `ApiExceptionHandler`

Technically this is already included among the project's 49 production source files; the numbering here counts conceptual sections rather than file count.

It is the centralized exception handler for the API.

It converts exceptions into HTTP responses.

Examples:

```text
NoSuchElementException
    → 404 Not Found

IllegalArgumentException
    → 400 Bad Request

HttpMessageNotReadableException
    → 400 Bad Request

IllegalStateException
    → 409 Conflict
```

Without this class, controllers would need repetitive:

```java
try {
   ...
} catch (...) {
   ...
}
```

logic.

---

# COMPLETE OBJECT RELATIONSHIP

The main domain relationship is:

```text
Tenant
│
├── Map<ScopeId, Scope>
│
├── Map<SubjectId, Subject>
│
├── Map<ResourceId, Resource>
│
└── Map<GrantId, EntitlementGrant>
```

Scopes create the hierarchy:

```text
Scope
│
├── parentScopeId
├── childScopeIds
└── subjectIds
```

Subjects belong to scopes:

```text
Subject
└── scopeId
```

Resources define available entitlement keys:

```text
Resource
│
└── EntitlementDefinitions
     ├── gpu.enabled
     ├── gpu.hours
     └── gpu.maxConcurrent
```

Grants assign those entitlements:

```text
EntitlementGrant
│
├── id
├── target
├── resourceId
├── entitlementKey
└── value
```

Usage belongs to the grant:

```text
Usage
│
├── grantId
├── consumed
├── periodStart
└── periodEnd
```

---

# COMPLETE EXAMPLE

Imagine Acme registers:

```text
Tenant: Acme

Root
└── Engineering
    ├── Backend
    │   ├── Alice
    │   └── Bob
    └── ML
        └── Charlie
```

They register:

```text
Resource:
    OpenAI API
```

with definitions:

```text
api.enabled
    BOOLEAN

api.requests
    QUOTA

api.maxBatch
    QUANTITY
```

Then Engineering receives:

```text
Grant 1

id:
    grant-eng-enabled

target:
    SCOPE:engineering

resource:
    openai-api

key:
    api.enabled

value:
    true
```

and:

```text
Grant 2

id:
    grant-eng-requests

target:
    SCOPE:engineering

resource:
    openai-api

key:
    api.requests

value:
    1,000,000/month
```

ML receives:

```text
Grant 3

id:
    grant-ml-requests

target:
    SCOPE:ml

resource:
    openai-api

key:
    api.requests

value:
    5,000,000/month
```

Now Alice asks:

```text
What is my api.requests entitlement?
```

Resolver:

```text
Alice
 ↓
Backend
 ↓
Engineering
 ↓
FOUND grant-eng-requests
```

Alice receives:

```text
1M/month
```

Charlie asks the same question:

```text
Charlie
 ↓
ML
 ↓
FOUND grant-ml-requests
```

Charlie receives:

```text
5M/month
```

---

# SHARED USAGE EXAMPLE

Alice consumes:

```text
100,000 requests
```

Bob consumes:

```text
200,000 requests
```

Both resolve to:

```text
grant-eng-requests
```

Therefore:

```text
Usage:

grantId = grant-eng-requests

consumed =
100000 + 200000
=
300000
```

Engineering now has:

```text
Limit:
1,000,000

Used:
300,000

Remaining:
700,000
```

If Alice later gets:

```text
Personal grant:
api.requests = 2M/month
```

then:

```text
Alice
 ↓
personal grant found immediately
```

Alice starts consuming from her own pool.

Bob continues consuming from Engineering's pool.

No special-case logic is needed.

It happens naturally because:

> nearest grant wins.

---

# TEST CLASSES

The project also contains test classes.

---

## `RegistrationServiceTest`

Tests tenant onboarding.

It verifies:

```text
recursive scopes are created

subjects are placed correctly

resources are registered

initial grants work

duplicate IDs fail

invalid grants fail

unknown targets fail

wrong entitlement value types fail
```

---

## `EntitlementResolverTest`

Tests inheritance.

Examples:

```text
subject override wins

nearest scope wins

parent fallback works

root fallback works

unknown subject fails

unknown resource fails
```

---

## `EntitlementServiceTest`

Tests evaluation semantics for:

```text
BOOLEAN

QUANTITY

QUOTA

RANGE

TIME_RANGE

SET

TEXT
```

---

## `UsageServiceTest`

Tests consumption behavior.

Important cases include:

```text
Alice and Bob share a department quota

subject override creates independent pool

quota cannot be exceeded

failed consumption doesn't change usage

exact remaining amount works

monthly quota resets

yearly quota resets

root grants can act as shared defaults

non-quota entitlements cannot be consumed
```

---

## `CommandServiceTest`

Tests runtime changes.

Examples:

```text
add scope

update scope

move scope

prevent hierarchy cycles

remove scope subtree

add subject

move subject

moving subject changes inherited entitlement

add resource

update resource

remove resource

set entitlement

replace entitlement

remove entitlement

fall back to parent after removing override
```

---

## `ApiIntegrationTest`

Tests the application through Spring HTTP endpoints rather than directly calling services.

It verifies:

```text
tenant registration through HTTP

tenant retrieval

entitlement evaluation

shared quota consumption

commands

HTTP validation errors

404 responses
```

---

## `MutableClock`

Test-only fake clock.

Allows tests to say:

```text
Current time = August 15
```

consume quota, then change time to:

```text
September 2
```

and verify that monthly usage resets.

No real waiting is needed.

---

## `TestFixtures`

Creates standard reusable example objects for tests.

For example:

```text
Acme
├── Engineering
│   ├── Backend
│   │   ├── Alice
│   │   └── Bob
│   └── ML
│       └── Charlie
└── Marketing
    └── Eve
```

Tests reuse this instead of reconstructing the same tenant repeatedly.

---

# RESPONSIBILITY SUMMARY

The easiest way to remember the important classes is:

```text
Tenant
    = one customer's entire world

Scope
    = hierarchical group

Subject
    = individual actor/entity

Resource
    = thing being managed

EntitlementDefinition
    = which controllable properties a resource supports

EntitlementValue
    = actual typed value

EntitlementGrant
    = assignment of one resource entitlement to one target

Target
    = which Scope or Subject owns that grant

EntitlementResolver
    = find which grant applies

EntitlementService
    = determine whether a requested operation is allowed

Usage
    = amount already consumed from a quota grant

UsageService
    = process actual consumption

TenantRegistry
    = in-memory tenant storage

UsageStore
    = in-memory usage storage

RegistrationService
    = build the original OOP tenant from registration JSON

CommandService
    = modify that OOP tenant later

Controllers
    = expose everything through HTTP/JSON
```

---

# THE MOST IMPORTANT FLOW

The core runtime path is:

```text
Alice sends request
        ↓
tenantId
subjectId
resourceId
entitlementKey
        ↓
TenantRegistry
        ↓
Tenant
        ↓
Subject Alice
        ↓
EntitlementResolver
        ↓

Alice grant?
    no

Alice's scope?
    no

Parent?
    yes
        ↓
Winning EntitlementGrant
        ↓
EntitlementService / UsageService
```

For consumption:

```text
Winning Grant
        ↓
grantId
        ↓
UsageStore
        ↓
Usage
        ↓
check remaining quota
        ↓
increment if allowed
```

---

# Architectural center of the project

The whole application can ultimately be summarized in one sentence:

> **A Tenant contains a recursive hierarchy of Scopes containing Subjects. The Tenant catalogs Resources, each Resource defines independently configurable entitlement keys, EntitlementGrants assign typed values for those keys to Scopes or Subjects, the nearest matching grant wins during inheritance, and consumable quota entitlements share usage through the winning grant's usage pool.**

That is the central model that the rest of our Spring Boot application is built around.
