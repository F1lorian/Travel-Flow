ALTER TABLE IF EXISTS user_trips DROP COLUMN IF EXISTS id;

-- Ausgabe (expense) ownership migration.
-- The Ausgabe entity gained a required `user_id`. On a database that already holds expenses,
-- ddl-auto=update cannot add a NOT NULL column to a populated table, so it fails on every boot.
-- Reconcile the data here: add the column nullable, backfill it, drop unattributable rows, then
-- enforce NOT NULL. Hibernate then adds the foreign key itself once the column exists. Every
-- statement is idempotent, so this is a no-op on a fresh database.
ALTER TABLE IF EXISTS ausgabe ADD COLUMN IF NOT EXISTS user_id bigint;

-- Attribute each existing expense to a member of its trip (any member is a valid owner).
UPDATE ausgabe a
SET user_id = (
    SELECT ut.user_id
    FROM user_trips ut
    WHERE ut.trip_id = a.trip_id
    ORDER BY ut.user_id
    LIMIT 1)
WHERE a.user_id IS NULL
  AND EXISTS (SELECT 1 FROM user_trips ut WHERE ut.trip_id = a.trip_id);

-- Expenses whose trip has no members cannot be attributed to anyone: drop this stale data.
DELETE FROM ausgabe WHERE user_id IS NULL;

ALTER TABLE IF EXISTS ausgabe ALTER COLUMN user_id SET NOT NULL;
