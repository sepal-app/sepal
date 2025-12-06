create table collection (
  id integer primary key autoincrement,
  collected_date text,
  collector text,
  habitat text,
  taxa text,
  remarks text,
  country text,
  province text,
  locality text,
  geo_coordinates blob,  -- SpatiaLite POINT geometry (registered via AddGeometryColumn)
  geo_uncertainty smallint check(geo_uncertainty > 0),
  elevation smallint,
  accession_id integer unique constraint collection_accession_id_fkey references accession (id) on delete cascade
);

-- Register the geometry column with SpatiaLite metadata
-- This enables spatial functions and proper geometry handling
-- Note: Requires SpatiaLite extension to be loaded; silently skipped if not available
SELECT AddGeometryColumn('collection', 'geo_coordinates', 4326, 'POINT', 'XY');
