-- Reverses V2__seed_support_config.sql.
-- Not run by Flyway (undo is a Teams feature) — apply by hand with psql, newest first:
--   psql "$DB_URL" -f src/main/resources/db/undo/U2__seed_support_config.sql
--
-- Tickets reference these rows, so this fails while any ticket still exists. That is deliberate:
-- deleting the catalogue under live tickets would leave them pointing at nothing.
--
-- Everything runs in one transaction so that failure is honest. psql without ON_ERROR_STOP
-- carries on to the next statement after an error, which would otherwise delete the history row
-- while the seed data survived — leaving Flyway convinced V2 had never run, and the next start
-- re-inserting it straight into ux_support_sections_module_code. Inside a transaction the failed
-- DELETE aborts the block and the history row stays put.

BEGIN;

DELETE FROM support_sections;
DELETE FROM support_modules;

DELETE FROM flyway_schema_history WHERE version = '2';

COMMIT;
