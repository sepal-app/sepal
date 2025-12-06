CREATE TABLE "schema_version" (version TEXT NOT NULL, applied_at TEXT DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE "user" (
  id integer primary key autoincrement,
  avatar_public_id text,
  email text not null unique,
  password text not null unique,
  email_verified_at text default null
) strict;
CREATE TABLE taxon (
  id integer primary key autoincrement,
  name text not null,
  author text,
  parent_id integer references taxon(id),
  rank text not null check(rank in (
    'class',
    'family',
    'form',
    'genus',
    'kingdom',
    'lusus',
    'order',
    'phylum',
    'prole',
    'section',
    'series',
    'species',
    'subclass',
    'subfamily',
    'subform',
    'subgenus',
    'subkingdom',
    'suborder',
    'subsection',
    'subseries',
    'subspecies',
    'subtribe',
    'subvariety',
    'superorder',
    'supertribe',
    'tribe',
    'unranked',
    'variety'
  )),
  wfo_taxon_id text,
  read_only integer not null default 0 check(read_only in (0, 1)),
  vernacular_names text not null default '[]' check(json_valid(vernacular_names))
) strict;
CREATE VIRTUAL TABLE taxon_fts using fts5(name, content='taxon', content_rowid='id');
CREATE TABLE accession (
  id integer primary key autoincrement,
  code text not null,
  taxon_id integer not null references taxon(id)
, private integer not null default 0 check(private in (0, 1)), id_qualifier text check(id_qualifier in (
  'aff',
  'cf',
  'forsan',
  'incorrect',
  'near',
  'questionable'
)), id_qualifier_rank text check(id_qualifier_rank in (
  'below_family',
  'family',
  'genus',
  'species',
  'first_infraspecific_epithet',
  'second_infraspecific_epithet',
  'cultivar'
)), provenance_type text check(provenance_type in (
  'wild',
  'cultivated',
  'not_wild',
  'purchase',
  'insufficient_data'
)), wild_provenance_status text check(wild_provenance_status in (
  'wild_native',
  'wild_non_native',
  'cultivated_native',
  'cultivated',
  'not_wild',
  'purchase',
  'insufficient_data'
)), supplier_contact_id integer references contact(id), date_received text, date_accessioned text) strict;
CREATE TABLE material (
  id integer primary key autoincrement,
  code text not null,
  accession_id integer not null references accession(id),
  location_id integer not null references location(id),
  type text not null default 'plant' check(type in ('plant', 'seed', 'vegetative', 'tissue', 'other')),
  status text not null default 'alive' check(status in ('dead', 'alive')),
  memorial integer not null default 0 check(memorial in (0, 1)),
  quantity integer not null default 1
) strict;
CREATE TABLE media (
  id integer primary key autoincrement,
  s3_bucket text not null,
  s3_key text not null,
  title text null,
  description text null,
  size_in_bytes integer not null,
  media_type text not null
) strict;
CREATE TABLE media_link (
  id integer primary key autoincrement,
  media_id integer not null unique,
  resource_id integer not null,
  resource_type text not null
) strict;
CREATE TABLE activity (
  id integer primary key autoincrement,
  data text not null check(json_valid(data)),
  type text not null,
  created_by integer not null references "user"(id),
  created_at text not null default (datetime('now'))
) strict;
CREATE TABLE settings (
  key text not null,
  value text,
  user_id integer references "user"(id)
) strict;
CREATE TABLE contact (
  id integer primary key autoincrement,
  name text not null,
  email text,
  address text,
  province text,
  postal_code text,
  country text,
  phone text,
  business text,
  notes text
) strict;
CREATE TABLE "location" (
  id integer primary key autoincrement,
  code text not null,
  name text not null,
  description text  -- nullable, no default
) strict;
CREATE TABLE collection (
  id integer primary key autoincrement,
  collected_date text,
  collector text,
  habitat text,
  taxa text,
  remarks text,
  country text,
  province text,
  locality text,
  geo_coordinates blob,  -- SpatiaLite POINT geometry
  geo_uncertainty smallint check(geo_uncertainty > 0),
  elevation smallint,
  accession_id integer unique constraint collection_accession_id_fkey references accession (id) on delete cascade
);
CREATE INDEX user_id_idx on "user" (id);
CREATE INDEX taxon_id_idx on taxon (id);
CREATE INDEX taxon_name_idx on taxon (name);
CREATE INDEX taxon_parent_id_idx on taxon (parent_id);
CREATE INDEX taxon_wfo_taxon_id_idx on taxon (wfo_taxon_id);
CREATE INDEX accession_id_idx on accession (id);
CREATE INDEX material_id_idx on material (id);
CREATE INDEX media_id_idx on media (id);
CREATE INDEX media_link_media_id_idx on media_link (media_id);
CREATE INDEX media_link_resource_id_resource_type_idx on media_link (resource_id, resource_type);
CREATE INDEX activity_id_idx on activity (id);
CREATE INDEX location_id_idx ON location (id);
CREATE TRIGGER trigger_taxon_after_insert after insert on taxon begin
  insert into taxon_fts(rowid, name) values (new.id, new.name);
end;
CREATE TRIGGER trigger_taxon_after_delete after delete on taxon begin
  insert into taxon_fts(taxon_fts, rowid, name) values('delete', old.id, old.name);
end;
CREATE TRIGGER trigger_taxon_after_update after update on taxon begin
  insert into taxon_fts(taxon_fts, rowid, name) values('delete', old.id, old.name);
  insert into taxon_fts(rowid, name) values (new.id, new.name);
end;
INSERT INTO "schema_version" (version) VALUES ('20251021111547');
INSERT INTO "schema_version" (version) VALUES ('20251101133108');
INSERT INTO "schema_version" (version) VALUES ('20251116002821');
INSERT INTO "schema_version" (version) VALUES ('20251128154132');
INSERT INTO "schema_version" (version) VALUES ('20251202211419');
