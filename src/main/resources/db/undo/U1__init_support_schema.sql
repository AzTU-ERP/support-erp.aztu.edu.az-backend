-- Reverses V1__init_support_schema.sql.
-- Not run by Flyway (undo is a Teams feature) — apply by hand with psql, after U2:
--   psql "$DB_URL" -f src/main/resources/db/undo/U1__init_support_schema.sql
--
-- Dropping in reverse dependency order; the extension is left in place because other migrations
-- (and other services sharing the instance) may still rely on it.
--
-- One transaction, for the same reason as U2: the history row must never outlive — or predecease
-- — the objects it describes.

BEGIN;

DROP TABLE IF EXISTS auth_sync_events;
DROP TABLE IF EXISTS user_violations;
DROP TABLE IF EXISTS ticket_status_history;
DROP TABLE IF EXISTS ticket_attachments;
DROP TABLE IF EXISTS tickets;
DROP SEQUENCE IF EXISTS support_ticket_reference_seq;
DROP TABLE IF EXISTS support_sections;
DROP TABLE IF EXISTS support_modules;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS users_ref;

DELETE FROM flyway_schema_history WHERE version = '1';

COMMIT;
