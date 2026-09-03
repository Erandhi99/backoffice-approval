# Back Office Client Approval System

A Spring Boot + MySQL backend implementing a 3-level approval workflow for
client onboarding requests (Entry Manager → Assistant Manager → Manager),
similar to an investment/asset-management back office.

## Why these design choices

- **State machine lives in one service** (`ClientRequestService`) so the
  workflow rules can never drift out of sync between different endpoints.
- **Flyway, not `ddl-auto: update`** — schema changes are explicit, versioned,
  reviewable SQL files. `ddl-auto: validate` just double-checks the JPA
  entities match what Flyway created; it never mutates the schema itself.
- **JWT + stateless sessions** — a back office API like this will eventually
  sit behind a load balancer; no server-side session state to replicate.
- **Full audit trail (`approval_history`)** is append-only and separate from
  the mutable `client_requests` row. This means you always know exactly who
  approved/rejected what and when, which any real approval system needs for
  compliance — not just "current state."
- **Optimistic locking (`@Version`)** on `client_requests` protects against
  two managers actioning the same request at the exact same moment.

## Tech stack

Java 17 · Spring Boot 3.3 · Spring Security (JWT) · Spring Data JPA ·
MySQL 8 · Flyway · springdoc-openapi (Swagger UI) · Lombok · JUnit 5

---

## 1. Prerequisites

- JDK 17+ (`java -version`)
- Maven 3.8+ (`mvn -version`) — or use the Maven wrapper if you generate one
- Docker Desktop running
- Git + a GitHub account

## 2. Start MySQL with Docker

From the project root:

```bash
docker compose up -d
```

This starts MySQL 8.4 on `localhost:3306` with:
- database: `backoffice_db`
- user: `backoffice_user` / `backoffice_pass`
- root password: `root_pass`

Check it's healthy:
```bash
docker ps          # STATUS should say "healthy"
docker logs backoffice-mysql --tail 20
```

Data persists in a named Docker volume (`backoffice_mysql_data`), so restarting
the container doesn't lose data. To wipe it and start fresh:
```bash
docker compose down -v
```

## 3. Run the application

```bash
mvn clean install      # downloads deps, compiles, runs unit tests
mvn spring-boot:run
```

On startup, Flyway automatically runs the migrations in
`src/main/resources/db/migration/`:
- `V1__init_schema.sql` — creates `users`, `client_requests`, `approval_history`
- `V2__seed_demo_users.sql` — inserts 4 demo accounts (see below)

The API is now at `http://localhost:8080`. Swagger UI (interactive API docs):
`http://localhost:8080/swagger-ui.html`

### Demo accounts (all passwords: `password123`)

| username    | role               |
|-------------|--------------------|
| client1     | CLIENT             |
| entrymgr1   | ENTRY_MANAGER      |
| asstmgr1    | ASSISTANT_MANAGER  |
| manager1    | MANAGER            |

## 4. Push to GitHub

```bash
git init
git add .
git commit -m "Initial commit: backoffice approval workflow"
git branch -M main

# Create an empty repo on GitHub first (github.com/new), then:
git remote add origin https://github.com/<your-username>/backoffice-approval.git
git push -u origin main
```

`.gitignore` already excludes `target/`, IDE files, and `.env` so you never
commit build output or secrets.

---

## 5. API walkthrough (the full workflow, end to end)

**Register / login** (get a JWT, then send it as `Authorization: Bearer <token>` on every other call):
```bash
curl -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"client1","password":"password123"}'
```

**1. Client submits a request:**
```bash
curl -X POST localhost:8080/api/requests \
  -H "Authorization: Bearer <CLIENT_TOKEN>" -H "Content-Type: application/json" \
  -d '{"name":"Nimal Perera","nic":"912345678V","address":"123 Galle Rd, Colombo","dateOfBirth":"1991-05-20"}'
```
→ status becomes `PENDING_ENTRY`.

**2. Entry manager reviews their queue and approves:**
```bash
curl localhost:8080/api/requests/queue -H "Authorization: Bearer <ENTRY_TOKEN>"
curl -X POST localhost:8080/api/requests/1/approve -H "Authorization: Bearer <ENTRY_TOKEN>"
```
→ status becomes `PENDING_ASSISTANT_MANAGER`.

**3. Assistant manager approves** (same pattern with `asstmgr1`'s token) →
status becomes `PENDING_MANAGER`.

**4. Manager approves** (same pattern with `manager1`'s token) → status
becomes `APPROVED`. The client is now fully onboarded.

**Rejection, at any stage:**
```bash
curl -X POST localhost:8080/api/requests/1/reject \
  -H "Authorization: Bearer <ANY_MANAGER_TOKEN>" -H "Content-Type: application/json" \
  -d '{"comment":"NIC number does not match the uploaded ID scan"}'
```
→ status becomes `REJECTED`, with `rejectionStage` and `rejectionComment` set.
The client sees exactly which stage rejected it and why via `GET /api/requests/my`.

**Client edits and resubmits after a rejection:**
```bash
curl -X PUT localhost:8080/api/requests/1 \
  -H "Authorization: Bearer <CLIENT_TOKEN>" -H "Content-Type: application/json" \
  -d '{"name":"Nimal Perera","nic":"912345678V","address":"CORRECTED address","dateOfBirth":"1991-05-20"}'
```
→ status resets to `PENDING_ENTRY` — it always restarts from the first
checkpoint, per your requirement. Attempting this on a request that isn't
`REJECTED` returns `409 Conflict`.

### Endpoint summary

| Method | Path                        | Role(s)                                    | Purpose |
|--------|-----------------------------|--------------------------------------------|---|
| POST   | `/api/auth/register`        | public                                     | create account (dev convenience — see hardening notes) |
| POST   | `/api/auth/login`           | public                                     | get JWT |
| POST   | `/api/requests`             | CLIENT                                     | submit new request |
| PUT    | `/api/requests/{id}`        | CLIENT (owner only)                        | edit + resubmit a rejected request |
| GET    | `/api/requests/my`          | CLIENT                                     | list own requests + full history |
| GET    | `/api/requests/{id}`        | CLIENT (own) or any manager                | request detail + history |
| GET    | `/api/requests/queue`       | ENTRY_MANAGER / ASSISTANT_MANAGER / MANAGER | requests waiting at *your* stage |
| POST   | `/api/requests/{id}/approve`| matching-stage manager                     | advance to next stage / APPROVED |
| POST   | `/api/requests/{id}/reject` | matching-stage manager                     | reject with mandatory comment |

---

## 6. Security hardening before production

This is a learning/reference build. Before going anywhere near production:

1. **Lock down `/api/auth/register`** — staff accounts (managers) should be
   provisioned by an admin, not self-registered. Only client sign-up should
   stay public, likely on a separate endpoint with email verification.
2. **Externalize `app.jwt.secret`** to an environment variable / secrets
   manager — never commit a real secret. Also add refresh tokens and shorter
   access-token TTLs.
3. **Rate-limit** `/api/auth/login` to slow brute force.
4. **Add HTTPS termination** (reverse proxy/load balancer) — JWTs in headers
   are only as safe as the transport.
5. **Field-level encryption** for NIC and address at rest, given this is
   PII for a financial services workflow.
6. **Pagination** on `/api/requests/queue` and `/api/requests/my` once data
   volume grows — both currently return unbounded lists.

## 7. Project structure

```
src/main/java/com/senfin/backoffice/
  config/        Spring Security + OpenAPI configuration
  security/      JWT generation/validation, UserDetailsService, JWT filter
  entity/        JPA entities + workflow enums (Role, ApprovalStage, RequestStatus, HistoryAction)
  repository/    Spring Data JPA repositories
  dto/           Request/response records (never expose entities directly)
  service/       AuthService, ClientRequestService (the workflow state machine)
  controller/    REST controllers
  exception/     Custom exceptions + @RestControllerAdvice global handler
src/main/resources/
  application.yml
  application-test.yml
  db/migration/  Flyway SQL scripts
src/test/        JUnit workflow tests (happy path, rejection, wrong-approver, edit/resubmit)
docker-compose.yml
```
