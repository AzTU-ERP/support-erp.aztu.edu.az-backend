# Support ERP — backend

The support module of AzTU's ERP (`erp.aztu.edu.az`). Anyone signed in can report a problem in any
module from the floating button in the shell; the ticket lands in a DEV Kanban queue, and repeated
irrelevant reports cost the reporter first a warning and then their sign-in.

Built to the same shape as `library-erp.aztu.edu.az-backend`: Spring Boot 4 on Java 17, PostgreSQL
owned by Flyway with Hibernate only validating, feature-sliced packages
(`controller` / `domain` / `dto` / `repository` / `service`), a single `ApiResponse` envelope and
a `GlobalExceptionHandler` that gives every failure the same shape.

**Authentication lives in the central SSO service.** There is no login here — the module reads the
bearer token, extracts the caller's identity and roles, and enforces authorization from that.

## Roles

| Role | Code in the token | What they can do |
|---|---|---|
| Reporter | *any authenticated account* | Open a ticket, read their own, see whether they have been warned |
| DEV | `dev` | The whole queue: board, filters, status changes, assignment, violations, unblocking |

Coarse gating is declared in `security/SecurityConfig`; per-row ownership ("is this the reporter's
own ticket?") is checked in the services, which are the only layer that can see the row. There is
no admin role above DEV — a DEV who can close a ticket can also lift a block.

`dev` is a role the auth service issues, like `library_admin` or `student`. If it does not exist
there yet it has to be added on that side; nothing in this service can grant it.

## Running locally

```bash
docker compose up -d postgres     # Postgres 16 on :5435, database "supportdb"
./mvnw spring-boot:run            # the service on :8083 (hr :8080, library :8081, alumni :8082)
```

Flyway creates the schema and seeds the module/section catalogue on first start. Swagger UI is at
<http://localhost:8083/swagger-ui.html>.

### Dev tokens

With `app.sso.dev-mode=true` and no introspection URL configured, a bearer token is just
`base64url("<uuid>:<roles>:<full name>:<email>")`. Any UUID works — a `users_ref` row is created on
first contact:

```bash
# a DEV
echo -n '55555555-5555-5555-5555-555555555555:dev:Dev Nurlan:nurlan.dev@aztu.edu.az' | basenc --base64url -w0
# an ordinary reporter
echo -n '33333333-3333-3333-3333-333333333333:student:Aysel Mammadova:aysel.mammadova@aztu.edu.az' | basenc --base64url -w0
```

Multiple roles are comma-separated (`student,dev`). Point `SSO_INTROSPECTION_URL` at the real auth
service to switch to remote validation; nothing else changes.

## Business rules

Every rule is enforced server-side. The frontend mirrors them in its form and its board, but only
as UX.

**The ticket lifecycle** (`ticket/service/TicketRules`)

```
OPEN      → IN_REVIEW, CANCELED, BLOCKED
IN_REVIEW → RESOLVED, CANCELED, BLOCKED
BLOCKED   → IN_REVIEW, CANCELED
RESOLVED  → (terminal)
CANCELED  → (terminal)
```

A ticket is always born `OPEN`, and only a DEV moves it. An illegal move is a `409`, not a `400`:
the board offered it, the ticket had already moved on. Every change is written to
`ticket_status_history`, including the one that opened the ticket.

Cancelling always states a reason — it is the only thing the reporter is told. `BLOCKED` here means
"waiting on something outside the DEV's hands", not "the reporter is blocked"; the two are
unrelated.

**The board** (`common/enums/BoardColumn`)

```
TO DO        = OPEN
IN PROGRESS  = IN_REVIEW, BLOCKED
DONE         = RESOLVED, CANCELED
```

A column is a view of statuses, not a status. Dropping a card into IN PROGRESS means `IN_REVIEW`;
dropping into DONE is genuinely ambiguous, so `defaultStatus` comes back null and the board asks
whether it was resolved or cancelled.

**Warnings and blocks** (`violation/service/ViolationRules`)

A DEV cancelling a ticket may mark it *irrelevant*, which is only possible as part of a
cancellation and always carries the reason. Each one adds 1 to the reporter's `irrelevant_count`:

| Count | Outcome |
|---|---|
| `>= app.support.violation.warn-threshold` (1) | Warned — the ticket form shows a banner saying the next one blocks them |
| `>= app.support.violation.block-threshold` (2) | Blocked — sign-in is refused until a DEV lifts it |

Both thresholds are configuration. Lifting a block resets the count to zero, so the reporter starts
over rather than sitting one report away from the next block. Only a DEV lifts it.

**Validation and limits**

- `module`/`section` must be a pair the catalogue currently offers — checked in `CatalogService`,
  and again by a composite foreign key in the schema.
- Description: 10–2000 characters. Stored verbatim and rendered as plain text by the frontend.
- Screenshots: at most 3 per ticket, at most 5 MB each, `image/png` / `image/jpeg` / `image/webp`
  only — checked by declared MIME **and** magic bytes, because the declared type is the uploader's
  claim. Filenames are sanitised to a display label and never used to build a path.
- Rate limit: 5 tickets per 10 minutes per reporter, counted from the rows themselves so it holds
  across restarts and instances.

## The contract with the auth service

Support **decides** who is blocked; auth **enforces** it at sign-in. The two halves:

**Push** — every block and unblock is written to the `auth_sync_events` outbox in the same
transaction as the decision, and `AuthSyncWorker` delivers it:

```
POST {AUTH_BASE_URL}/auth/internal/users/{ssoUserId}/block
POST {AUTH_BASE_URL}/auth/internal/users/{ssoUserId}/unblock
X-Service-Token: {SERVICE_TOKEN}
{"userId":"…","action":"BLOCK","reason":"…","source":"support-erp"}
```

Failures are retried up to `app.auth.max-attempts` with the error kept on the row. With
`AUTH_BASE_URL` unset — the local default — the rows simply queue.

**Pull** — the same answer, for an auth service that would rather ask at sign-in than store a flag:

```
GET /api/support/internal/users/{ssoUserId}/block-status
X-Service-Token: {SERVICE_TOKEN}
→ { "userId": "…", "blocked": true, "blockedAt": "…", "irrelevantCount": 2, "reason": "…" }
```

`reason` is the wording to show the person being turned away, so support and auth cannot say two
different things about the same block.

**What the auth service still has to do** (its repository is not in this workspace):

1. Accept the two internal endpoints above, or poll the block-status one.
2. Refuse sign-in for a blocked account with `403` and the returned `reason` — distinguishable
   from a wrong password, which the shell's sign-in form relies on.
3. Revoke the account's live refresh tokens when a block arrives, so an open session does not
   outlive it.
4. Issue the `dev` role to the developers who should see the board.

## API

Everything is under `/api/support`. Bodies are camelCase and wrapped in the usual
`{success, message, data, timestamp}` envelope.

**Any authenticated account**

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/support/me` | Identity and roles, for the shell's route guard |
| `GET` | `/api/support/config/modules` | Modules with their sections and route prefixes |
| `POST` | `/api/support/tickets` | `multipart/form-data`: `module`, `section`, `description`, `pageUrl`, `userAgent`, `screenshots` (0–3) |
| `GET` | `/api/support/tickets/my` | `page`, `size` |
| `GET` | `/api/support/tickets/my/{id}` | 404 rather than 403 for somebody else's |
| `GET` | `/api/support/tickets/{id}/attachments/{attachmentId}` | The reporter's own, or any DEV's |
| `GET` | `/api/support/me/violation-status` | `{irrelevantCount, warned, blocked, warnThreshold, blockThreshold, remainingBeforeBlock}` |

**DEV only**

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/support/tickets` | `status`, `module`, `section`, `userId`, `assignedTo`, `from`, `to`, `q`, `page`, `size`, `sort`, `direction` |
| `GET` | `/api/support/tickets/board` | Grouped into TO_DO / IN_PROGRESS / DONE; `module`, `section`, `assignedTo`, `q`, `status`, `from`, `to`. `status` narrows each column rather than replacing them — a column that status cannot appear in comes back empty. |
| `GET` | `/api/support/tickets/{id}` | Detail with attachments and history |
| `PATCH` | `/api/support/tickets/{id}/status` | `{status, comment?, irrelevant?, cancelReason?}` |
| `PATCH` | `/api/support/tickets/{id}/assign` | `{devId}`; null hands it back to the queue |
| `GET` | `/api/support/devs` | Assignee picker |
| `GET` | `/api/support/violations` | `blockedOnly`, `page`, `size` |
| `POST` | `/api/support/violations/{userId}/unblock` | `userId` is the local `users_ref` id from the list |

**Service token only** — `X-Service-Token`, not a bearer token

| Method | Path |
|---|---|
| `GET` | `/api/support/internal/users/{ssoUserId}/block-status` |

## Tests

```bash
./mvnw test
```

`TicketRulesTest`, `ViolationRulesTest` and `FileStorageServiceTest` run anywhere — the rules are
deliberately free of Spring and JPA, and the storage tests work in a temp directory.

`TicketFlowIntegrationTest` and `SupportErpApplicationTests` need a live Postgres and are
`@Disabled` by default, the same way `library-erp`'s context test is. With `compose.yaml` up:

```bash
docker compose up -d postgres
./mvnw test -Dtest=TicketFlowIntegrationTest
```

It covers the two flows that span the whole service — opening a ticket with screenshots, and a
reporter being warned, blocked and unblocked — over HTTP, through the real security filters, and
rolls each test back.

## Configuration

| Variable | Default | What it does |
|---|---|---|
| `SERVER_PORT` | `8083` | |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5435/supportdb`, `myuser`, `secret` | |
| `STORAGE_BASE_PATH` | `./support-storage` | Where screenshots land |
| `STORAGE_MAX_SIZE` | `5242880` | Bytes per screenshot |
| `CORS_ORIGINS` | `http://localhost:3000,https://erp.aztu.edu.az` | The shell |
| `SSO_INTROSPECTION_URL` | *(empty)* | Set it to validate tokens remotely |
| `SSO_DEV_MODE` | `true` | Local base64 tokens. **Turn off in production.** |
| `VIOLATION_WARN_THRESHOLD` | `1` | Irrelevant reports before a warning |
| `VIOLATION_BLOCK_THRESHOLD` | `2` | Irrelevant reports before a block |
| `TICKET_RATE_LIMIT_MAX` | `5` | Tickets per window, per reporter |
| `TICKET_RATE_LIMIT_WINDOW_MINUTES` | `10` | |
| `TICKET_MAX_ATTACHMENTS` | `3` | Screenshots per ticket |
| `AUTH_BASE_URL` | *(empty)* | The auth service. Empty means block decisions queue undelivered |
| `SERVICE_TOKEN` | `change-me-support-service-token` | Shared secret both ways. **Override in production.** |
| `AUTH_SYNC_MAX_ATTEMPTS` | `5` | Outbox delivery ceiling |
| `AUTH_SYNC_POLL_INTERVAL_MS` | `60000` | Outbox worker cadence |

## Migrations

`src/main/resources/db/migration` is what Flyway runs. `src/main/resources/db/undo` holds the
matching rollback scripts — Flyway Community has no `undo`, so they are applied by hand, newest
first:

```bash
psql "$DB_URL" -f src/main/resources/db/undo/U2__seed_support_config.sql
psql "$DB_URL" -f src/main/resources/db/undo/U1__init_support_schema.sql
```

U2 refuses to run while any ticket exists, which is deliberate: the catalogue cannot be deleted
out from under live tickets.
