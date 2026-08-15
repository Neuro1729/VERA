# VERA

**VERA** — Versatile Entitlement & Resource Authorization

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
git clone https://github.com/Neuro1729/VERA.git
cd VERA
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

### Admin UI (Vite)

Local development keeps the React app and Spring Boot on separate ports. Vite proxies `/api` to Spring Boot so the browser stays same-origin and sends the HttpOnly `JSESSIONID` cookie.

```bash
# terminal 1
mvn spring-boot:run

# terminal 2
cd frontend
npm install
npm run dev
```

UI: `http://localhost:5173`  
API proxy: `/api/*` → `http://localhost:8080`

Production frontend artifact:

```bash
cd frontend
npm run build
```

Output is `frontend/dist`. Backend unit tests do not require Node. Serve `frontend/dist` with any static host, or put a reverse proxy in front of both `/` (SPA) and `/api` (Spring Boot).

First start against an empty `entitlements` database applies Flyway migrations automatically.

### Smoke the API

Preview remains public. Secure company creation is `POST /api/auth/signup` (CSRF + session). Gateway calls need `X-VERA-API-KEY`.

```bash
curl -sS -X POST http://localhost:8080/api/company-registration/preview \
  -H "Content-Type: application/json" \
  --data-binary @examples/company-registration.json
```

See **Authentication (V1)** below for signup/login/gateway curl with cookies and CSRF.

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
mvn "-Dtest=PostgresPersistenceIT,PostgresHttpIT,PostgresBulkSyncIT,PostgresSecurityIT" test
```

One class or method:

```bash
./scripts/test-one.sh EntitlementResolverTest
./scripts/test-one.sh 'UsageServiceTest#usersResolvingToSameDepartmentGrantShareOneQuotaPool'
```

`./scripts/test-all.sh` runs the full suite and prints PASS/FAIL (needs `python3` and a Unix shell).

## HTTP API

```text
GET  /api/auth/csrf
POST /api/auth/signup
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
GET  /api/auth/api-key
POST /api/auth/api-key/rotate

POST /api/company-registration/preview   # public, side-effect free
POST /api/auth/signup                    # public secure company creation

GET  /api/tenants                        # authenticated admin's tenant only
GET  /api/tenants/{tenantId}
GET  /api/tenants/{tenantId}/usage

POST /api/commands                       # small edits

POST /api/tenants/{tenantId}/sync/preview
POST /api/tenants/{tenantId}/sync
POST /api/tenants/{tenantId}/sync/organization[/preview]
POST /api/tenants/{tenantId}/sync/resources[/preview]
POST /api/tenants/{tenantId}/sync/grants[/preview]

POST /api/gateway/tenants/{tenantId}/evaluate
POST /api/gateway/tenants/{tenantId}/consume
POST /api/gateway/tenants/{tenantId}/rate-limit/consume
POST /api/gateway/tenants/{tenantId}/use

POST /api/entitlements/evaluate          # admin debug; tenantId in body
POST /api/entitlements/consume
POST /api/entitlements/rate-limit/consume
POST /api/entitlements/use

GET  /api/tenants/{tenantId}/resources/{resourceId}/distribution?scopeId=
GET  /api/tenants/{tenantId}/resources/{resourceId}/live
GET  /api/tenants/{tenantId}/resources/{resourceId}/entitlement-history
GET  /api/tenants/{tenantId}/resources/{resourceId}/usage-history
GET  /api/tenants/{tenantId}/entitlement-history
GET  /api/tenants/{tenantId}/usage-history
```

When `vera.security.enabled=true` (the default), `POST /api/company-registration` and `POST /api/tenants/register` are not anonymous creation paths. Use `POST /api/auth/signup`. Preview stays public. Existing tests/dev fixtures can set `vera.security.enabled=false` to keep the older unauthenticated apply endpoints.

Sample JSON lives in `examples/`. Domain and resolution rules: `architecture-decisions.md`.

## Authentication (V1)

Two actors, no JWT/SSO.

| Actor | Credential | Transport | Access |
| --- | --- | --- | --- |
| One human admin per tenant | email + password | server-side `HttpSession`, `JSESSIONID` cookie | management APIs + VERA UI |
| One company backend per tenant | VERA API key | `X-VERA-API-KEY` header | `/api/gateway/**` only |

The server stores the authenticated session. The browser/client keeps only `JSESSIONID` (HttpOnly, SameSite=Lax, 30 minute timeout). Set `VERA_SESSION_COOKIE_SECURE=true` in HTTPS production; leave it unset/false for localhost.

The React admin UI never stores `JSESSIONID`, passwords, or the runtime API key in `localStorage` / `sessionStorage`. Authentication state comes from `GET /api/auth/me`. The frontend never calls the runtime gateway with the company API key.

Raw API keys are returned **once** at signup and on rotate. The database stores `publicId` + `secretHash` only. An API key is bound to exactly one tenant. Path/body `tenantId` is never trusted as authentication.

```bash
# 1. CSRF cookie + token (required for cookie-authenticated POST/PUT/DELETE)
curl -c cookies.txt -sS http://localhost:8080/api/auth/csrf

# 2. Signup (creates tenant + admin + API key, then logs the admin in)
TOKEN=$(python -c "import json; print(json.load(open('csrf.json'))['token'])")  # or copy token from step 1
curl -c cookies.txt -b cookies.txt -sS -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"admin":{"email":"admin@acme.com","password":"a-long-password"},"registration": ...}'

# 3. Or login later (reuse cookie jar so JSESSIONID is stored)
curl -c cookies.txt -b cookies.txt -sS http://localhost:8080/api/auth/csrf
curl -c cookies.txt -b cookies.txt -sS -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"email":"admin@acme.com","password":"a-long-password"}'

curl -b cookies.txt -sS http://localhost:8080/api/auth/me

# 4. Company runtime (stateless; no CSRF; no JSESSIONID)
curl -sS -X POST http://localhost:8080/api/gateway/tenants/acme/evaluate \
  -H "Content-Type: application/json" \
  -H "X-VERA-API-KEY: vera_live_...." \
  -d '{"subjectId":"emp-1001","resourceId":"gpu","entitlementKey":"gpu.enabled","requestedValue":true}'
```

Postman/curl must send both the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header for management mutations. Gateway calls must not use the admin session; admin calls must not use the API key.

V1 does not include JWT, OAuth, OIDC, SAML, refresh tokens, or multiple admins per tenant.

## Company onboarding

A company provides three JSON documents: **organization**, **resources**, **grants**. The UI, HTTP API, and signup all submit the same shapes; the backend does not read config files.

```text
three configs → preview → cross-validation → atomic register
```

`POST /api/company-registration/preview` is side-effect free. Secure apply is `POST /api/auth/signup`, which creates the tenant, admin, and API key in one transaction.

## Ongoing configuration

| Change size | API |
| --- | --- |
| Small edit | `POST /api/commands` |
| Organization / resources / grants catalog | domain `sync` endpoints |
| Coordinated cross-domain change | combined `POST /api/tenants/{id}/sync` |

Each bulk domain independently uses `MERGE` (only supplied objects change) or `RECONCILE` (submitted document is desired final state for that domain). Combined requests may omit any domain; at least one is required. Always preview before apply.

Validation always runs against the **projected final Tenant**, not an isolated JSON section.

### Cross-validation example

Resource RECONCILE removes definition `gpu.hours` while 14 grants still reference it:

- `invalidGrantCount = 14`
- apply is blocked

If the same combined request also RECONCILE-removes those 14 grants, preview is valid and apply succeeds atomically.

Scope/resource **deletion** is different: existing `REMOVE_SCOPE` / `REMOVE_RESOURCE` cascade-purge grants. Preview reports `grantsAutomaticallyRemoved` and can still be `valid` with `invalidGrantCount = 0`.

## Runtime gateway

Company backend (not end users) calls:

```text
POST /api/gateway/tenants/{tenantId}/evaluate
POST /api/gateway/tenants/{tenantId}/consume
POST /api/gateway/tenants/{tenantId}/rate-limit/consume
POST /api/gateway/tenants/{tenantId}/use
```

This is machine-to-machine. There is no user redirect. Authenticate with `X-VERA-API-KEY`. The key's tenant must match the path `tenantId` or the call returns HTTP 403. The engine returns ALLOW/DENY/remaining and may update usage; it does **not** allocate GPUs or call company systems. Quota/rate-limit denial remains HTTP 200 `allowed=false`.

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
