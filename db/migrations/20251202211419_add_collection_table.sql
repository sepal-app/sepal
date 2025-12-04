-- migrate:up
PRAGMA enable_load_extension = 1;

-- select sqlite3_enable_load_extension();
select load_extension('mod_spatialite');

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
  -- geo_coordinates geography(POINT),
  gps_datum text,
  -- TODO: constraint for positive value
  geo_uncertainty smallint,
  -- TODO: constraint for positive value
  elevation smallint -- ,
  -- accession_id int unique constraint collection_accession_id_fkey references accession (id) on delete cascade
)



-- migrate:down
