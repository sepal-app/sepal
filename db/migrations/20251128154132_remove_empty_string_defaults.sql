-- SQLite doesn't support ALTER COLUMN, so we need to recreate the table
-- First, create a new table with the correct schema
CREATE TABLE location_new (
  id integer primary key autoincrement,
  code text not null,
  name text not null,
  description text  -- nullable, no default
);

-- Copy data, converting empty strings to null
INSERT INTO location_new (id, code, name, description)
SELECT id, code, name, NULLIF(description, '') FROM location;

-- Drop old table and rename
DROP TABLE location;
ALTER TABLE location_new RENAME TO location;

-- Recreate index
CREATE INDEX location_id_idx ON location (id);

