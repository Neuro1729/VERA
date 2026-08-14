# Resource Entitlement Engine

Generic hierarchical resource-entitlement engine (Java 21 + Spring Boot 3.5).

A tenant owns recursive scopes, subjects, resources, and typed entitlement grants. Nearest grant wins (subject → current scope → parents → root). Quota and rate-limit pools belong to the **winning grant**, not the subject. PostgreSQL is the durable source of truth; unit tests stay in-memory.

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | `java -version` must show 21 |
| PostgreSQL | 14+ (18 works) | listening on `localhost:5432` |
| Maven | 3.9+ | optional if you use the wrapper from Git Bash / WSL / macOS / Linux |

On Windows, `mvnw` is a shell script. Use Git Bash / WSL (`./mvnw ...`) or a local Maven install (`mvn ...`).

## Clone

```bash
git clone <this-repo-url>
cd resource-entitlement-engine
```

## PostgreSQL setup

Create two empty databases. Flyway creates the schema on startup and during Postgres tests.

```sql
CREATE DATABASE entitlements;
CREATE DATABASE entitlements_test;
```

Repo defaults (override if your install differs):

```text
host     localhost:5432
user     postgres
password 12345
```

| Database | Used by |
| --- | --- |
| `entitlements` | `./mvnw spring-boot:run` |
| `entitlements_test` | PostgreSQL integration tests (truncated between tests) |

If your password is not `12345`:

```bash
# Unix / Git Bash
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your-password

# PowerShell
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="your-password"
```

Optional URL override: `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/entitlements`

## Run the app

```bash
./mvnw spring-boot:run
# or: mvn spring-boot:run
```

Server: `http://localhost:8080`

First start against an empty `entitlements` database applies Flyway migrations automatically.

### Smoke the API

```bash
curl -sS -X POST http://localhost:8080/api/tenants/register \
  -H "Content-Type: application/json" \
  --data-binary @examples/acme-registration.json

curl -sS -X POST http://localhost:8080/api/entitlements/evaluate \
  -H "Content-Type: application/json" \
  --data-binary @examples/evaluate-alice.json

curl -sS -X POST http://localhost:8080/api/entitlements/consume \
  -H "Content-Type: application/json" \
  --data-binary @examples/consume-alice.json
```

## Tests

```bash
./mvnw test
# or: mvn test
```

| Kind | Needs PostgreSQL? | How |
| --- | --- | --- |
| Unit / API tests | No | `memory` profile (default for tests) |
| Persistence / HTTP-reload tests | Yes (`entitlements_test`) | classes under `com.example.entitlements.persistence` |

Postgres-only:

```bash
mvn "-Dtest=PostgresPersistenceIT,PostgresHttpIT" test
```

One class or method:

```bash
./scripts/test-one.sh EntitlementResolverTest
./scripts/test-one.sh 'UsageServiceTest#usersResolvingToSameDepartmentGrantShareOneQuotaPool'
```

`./scripts/test-all.sh` runs the full suite and prints PASS/FAIL (needs `python3` and a Unix shell).

## HTTP API

```text
POST /api/tenants/register
GET  /api/tenants
GET  /api/tenants/{tenantId}
GET  /api/tenants/{tenantId}/usage

POST /api/commands

POST /api/entitlements/evaluate          # read-only
POST /api/entitlements/consume           # QUOTA
POST /api/entitlements/rate-limit/consume
POST /api/entitlements/use               # non-consumable committed use

GET  /api/tenants/{tenantId}/resources/{resourceId}/distribution?scopeId=
GET  /api/tenants/{tenantId}/resources/{resourceId}/live
GET  /api/tenants/{tenantId}/resources/{resourceId}/entitlement-history
GET  /api/tenants/{tenantId}/resources/{resourceId}/usage-history
```

Sample JSON lives in `examples/`. Domain and resolution rules: `architecture-decisions.md`.

## Troubleshooting

**`Connection refused` / `Failed to configure a DataSource`**
PostgreSQL is not running on `localhost:5432`. Start the service, then retry.

**`password authentication failed for user "postgres"`**
Your local password is not `12345`. Set `SPRING_DATASOURCE_PASSWORD` (see above). Do not change `pg_hba.conf` unless you know you need to.

**`database "entitlements" does not exist`** (or `entitlements_test`)
Create the databases in the SQL block above. The app does not create them.

**`FATAL: role "postgres" does not exist`**
Use the superuser your install actually created, via `SPRING_DATASOURCE_USERNAME`.

**App starts, tests pass, but Postgres ITs fail**
Unit tests do not need a database. ITs need `entitlements_test` plus the same credentials.

**Port 8080 already in use**
Stop the other process, or run with `--server.port=8081`.

**Wrong Java version**
The build targets Java 21. `JAVA_HOME` must point at a JDK 21.

**Windows `./mvnw` is not recognized**
Use Git Bash, or install Maven and run `mvn` instead.
