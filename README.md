# Resource Entitlement Engine

Minimal Spring Boot V1 of a generic, hierarchical resource-entitlement engine.

## Requirements

- Java 21
- Linux/macOS shell
- Internet access on first Maven-wrapper run if Maven is not already installed

The project pins Spring Boot 3.5.16 and Java 21. Spring's documentation lists 3.5.16 as a stable Spring Boot line; the code intentionally stays on the 3.x/Jackson-2 ecosystem for a conservative V1.

## Run all tests

```bash
./scripts/test-all.sh
```

The script prints every JUnit test method as `PASS`/`FAIL` after Maven completes.

## Run one test class or pattern

```bash
./scripts/test-one.sh EntitlementResolverTest
./scripts/test-one.sh 'UsageServiceTest#usersResolvingToSameDepartmentGrantShareOneQuotaPool'
```

## Run application

```bash
./scripts/run.sh
```

Server: `http://localhost:8080`

## API

```text
POST /api/tenants/register
GET  /api/tenants/{tenantId}
GET  /api/tenants/{tenantId}/usage
POST /api/commands
POST /api/entitlements/evaluate
POST /api/entitlements/consume
POST /api/entitlements/rate-limit/consume
```

## Try the sample registration

```bash
curl -sS -X POST http://localhost:8080/api/tenants/register \
  -H 'Content-Type: application/json' \
  --data-binary @examples/acme-registration.json
```

Evaluate Alice's quota:

```bash
curl -sS -X POST http://localhost:8080/api/entitlements/evaluate \
  -H 'Content-Type: application/json' \
  --data-binary @examples/evaluate-alice.json
```

Consume usage:

```bash
curl -sS -X POST http://localhost:8080/api/entitlements/consume \
  -H 'Content-Type: application/json' \
  --data-binary @examples/consume-alice.json
```

Change the Backend scope quota:

```bash
curl -sS -X POST http://localhost:8080/api/commands \
  -H 'Content-Type: application/json' \
  --data-binary @examples/set-backend-entitlement.json
```

## Tests

JUnit tests live under `src/test/java` (standard Maven layout). The top-level `tests/` folder contains API smoke-test assets and request fixtures.

See `architecture-decisions.md` for the complete design rationale and class model.
