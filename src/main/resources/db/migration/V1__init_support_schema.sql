-- ============================================================
-- Support Module — AzTU (OneCampus)
-- Auth via central SSO microservice (NO local login here).
-- users_ref mirrors the SSO identity; passwords never land in this database.
-- Blocking a user is decided here and pushed to the auth service through the
-- auth_sync_events outbox — see AuthSyncWorker.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---- Reporters and DEVs, projected from the SSO token on first contact ----
CREATE TABLE users_ref (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sso_user_id UUID UNIQUE NOT NULL,
  full_name   VARCHAR,
  email       VARCHAR,
  created_at  TIMESTAMP DEFAULT now(),
  updated_at  TIMESTAMP DEFAULT now()
);

-- A user can hold several roles at once (e.g. a DEV who is also a lecturer).
CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES users_ref(id) ON DELETE CASCADE,
  role    VARCHAR NOT NULL,
  PRIMARY KEY (user_id, role)
);

-- ---- Reportable surface: which modules exist and what they are divided into ----
-- Seeded in V2 and editable afterwards; nothing about it is hardcoded in the app.
CREATE TABLE support_modules (
  code         VARCHAR PRIMARY KEY,
  name         VARCHAR NOT NULL,
  -- Lets the frontend preselect the module from the current route (/lms/... -> LMS).
  route_prefix VARCHAR,
  sort_order   INT NOT NULL DEFAULT 0,
  is_active    BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMP DEFAULT now(),
  updated_at   TIMESTAMP DEFAULT now(),
  CONSTRAINT ck_support_modules_code CHECK (
    code IN ('LMS', 'HR', 'LIBRARY', 'FINANCE', 'EXAM', 'TURNIKET'))
);

CREATE TABLE support_sections (
  id          SERIAL PRIMARY KEY,
  module_code VARCHAR NOT NULL REFERENCES support_modules(code) ON DELETE CASCADE,
  code        VARCHAR NOT NULL,
  name        VARCHAR NOT NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMP DEFAULT now(),
  updated_at  TIMESTAMP DEFAULT now(),
  CONSTRAINT ux_support_sections_module_code UNIQUE (module_code, code)
);
CREATE INDEX ix_support_sections_module ON support_sections (module_code) WHERE is_active = TRUE;

-- ---- Tickets ----
-- Human-facing number (SUP-000001); the UUID stays the identifier everywhere else.
CREATE SEQUENCE support_ticket_reference_seq START 1;

CREATE TABLE tickets (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reference       VARCHAR UNIQUE NOT NULL,
  user_id         UUID NOT NULL REFERENCES users_ref(id),
  module          VARCHAR NOT NULL,
  section         VARCHAR NOT NULL,
  description     TEXT NOT NULL,
  status          VARCHAR NOT NULL DEFAULT 'OPEN',
  is_irrelevant   BOOLEAN NOT NULL DEFAULT FALSE,
  cancel_reason   TEXT,
  assigned_dev_id UUID REFERENCES users_ref(id),
  page_url        VARCHAR,
  user_agent      VARCHAR,
  resolved_at     TIMESTAMP,
  created_at      TIMESTAMP DEFAULT now(),
  updated_at      TIMESTAMP DEFAULT now(),
  CONSTRAINT ck_tickets_status CHECK (
    status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'CANCELED', 'BLOCKED')),
  CONSTRAINT ck_tickets_description_length CHECK (char_length(description) BETWEEN 10 AND 2000),
  -- Cancelling always states a reason; it is what the reporter is shown afterwards.
  CONSTRAINT ck_tickets_cancel_reason CHECK (status <> 'CANCELED' OR cancel_reason IS NOT NULL),
  -- "Irrelevant" is a property of a cancellation, never of a ticket still in play.
  CONSTRAINT ck_tickets_irrelevant_only_canceled CHECK (is_irrelevant = FALSE OR status = 'CANCELED'),
  -- The module/section pair must be one the catalogue actually offers. A section in use
  -- therefore cannot be deleted — deactivate it instead, which leaves old tickets readable.
  CONSTRAINT fk_tickets_section FOREIGN KEY (module, section)
    REFERENCES support_sections (module_code, code)
);
CREATE INDEX ix_tickets_user ON tickets (user_id, created_at DESC);
CREATE INDEX ix_tickets_status ON tickets (status, created_at DESC);
CREATE INDEX ix_tickets_module_section ON tickets (module, section);
CREATE INDEX ix_tickets_assigned ON tickets (assigned_dev_id) WHERE assigned_dev_id IS NOT NULL;

CREATE TABLE ticket_attachments (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id     UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
  storage_key   VARCHAR NOT NULL,
  original_name VARCHAR,
  mime_type     VARCHAR NOT NULL,
  size_bytes    BIGINT NOT NULL,
  created_at    TIMESTAMP DEFAULT now(),
  updated_at    TIMESTAMP DEFAULT now(),
  -- Screenshots only: anything else is rejected before it reaches disk (MIME + magic bytes).
  CONSTRAINT ck_ticket_attachments_mime CHECK (
    mime_type IN ('image/png', 'image/jpeg', 'image/webp')),
  CONSTRAINT ck_ticket_attachments_size CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);
CREATE INDEX ix_ticket_attachments_ticket ON ticket_attachments (ticket_id);

-- Every status change, including the one that opens the ticket (from_status IS NULL).
CREATE TABLE ticket_status_history (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id   UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
  from_status VARCHAR,
  to_status   VARCHAR NOT NULL,
  changed_by  UUID REFERENCES users_ref(id),
  comment     TEXT,
  created_at  TIMESTAMP DEFAULT now(),
  updated_at  TIMESTAMP DEFAULT now()
);
CREATE INDEX ix_ticket_status_history_ticket ON ticket_status_history (ticket_id, created_at);

-- ---- Warning / blocking ledger ----
-- This service decides; the auth service enforces at sign-in. One row per user, created the
-- first time one of their tickets is cancelled as irrelevant.
CREATE TABLE user_violations (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID UNIQUE NOT NULL REFERENCES users_ref(id) ON DELETE CASCADE,
  irrelevant_count    INT NOT NULL DEFAULT 0,
  warned_at           TIMESTAMP,
  blocked_at          TIMESTAMP,
  blocked_by_dev_id   UUID REFERENCES users_ref(id),
  unblocked_at        TIMESTAMP,
  unblocked_by_dev_id UUID REFERENCES users_ref(id),
  created_at          TIMESTAMP DEFAULT now(),
  updated_at          TIMESTAMP DEFAULT now(),
  CONSTRAINT ck_user_violations_count CHECK (irrelevant_count >= 0)
);
CREATE INDEX ix_user_violations_blocked ON user_violations (blocked_at) WHERE blocked_at IS NOT NULL;

-- ---- Outbox: block/unblock deliveries to the auth service ----
-- Same shape as hr-erp's integration_events. The row is written in the same transaction as the
-- decision, so a delivery can never be lost because the auth service happened to be down.
CREATE TABLE auth_sync_events (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type  VARCHAR NOT NULL,
  sso_user_id UUID NOT NULL,
  payload     JSONB NOT NULL,
  status      VARCHAR NOT NULL DEFAULT 'pending',
  attempts    INT NOT NULL DEFAULT 0,
  last_error  TEXT,
  sent_at     TIMESTAMP,
  created_at  TIMESTAMP DEFAULT now(),
  updated_at  TIMESTAMP DEFAULT now(),
  CONSTRAINT ck_auth_sync_events_type CHECK (event_type IN ('BLOCK', 'UNBLOCK')),
  CONSTRAINT ck_auth_sync_events_status CHECK (status IN ('pending', 'sent', 'failed'))
);
CREATE INDEX ix_auth_sync_events_pending ON auth_sync_events (status, created_at)
  WHERE status IN ('pending', 'failed');
