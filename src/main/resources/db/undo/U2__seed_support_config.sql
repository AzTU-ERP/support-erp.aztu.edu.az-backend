-- Reverses V2__seed_support_config.sql.
-- Not run by Flyway (undo is a Teams feature) — apply by hand with psql, newest first:
--   psql "$DB_URL" -f src/main/resources/db/undo/U2__seed_support_config.sql
-- Tickets reference these rows, so this fails while any ticket still exists. That is deliberate:
-- deleting the catalogue under live tickets would leave them pointing at nothing.

DELETE FROM support_sections;
DELETE FROM support_modules;

DELETE FROM flyway_schema_history WHERE version = '2';
