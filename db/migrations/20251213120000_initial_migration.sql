pragma foreign_keys = on;

-- ============================================================================
-- USER TABLE
-- ============================================================================
create table "user" (
  id integer primary key autoincrement,
  avatar_public_id text,
  email text not null unique,
  password text not null unique,
  email_verified_at text default null,
  full_name text,
  role text not null check(role in ('admin', 'editor', 'reader')),
  status text not null default 'active' check(status in ('invited', 'active', 'archived')),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

create index user_id_idx on "user" (id);
create index user_status_idx on "user" (status);
create index user_role_idx on "user" (role);

create trigger trigger_user_updated_at after update on "user"
begin
  update "user" set updated_at = datetime('now') where id = NEW.id;
end;

-- ============================================================================
-- TAXON TABLE
-- ============================================================================
create table taxon (
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
  vernacular_names text not null default '[]' check(json_valid(vernacular_names)),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

create index taxon_id_idx on taxon (id);
create index taxon_name_idx on taxon (name);
create index taxon_parent_id_idx on taxon (parent_id);
create index taxon_wfo_taxon_id_idx on taxon (wfo_taxon_id);

create trigger trigger_taxon_updated_at after update on taxon
begin
  update taxon set updated_at = datetime('now') where id = NEW.id;
end;

-- Taxon full text search
create virtual table taxon_fts using fts5(name, content='taxon', content_rowid='id');

insert into taxon_fts(rowid, name) select id, name from taxon;

-- Triggers to keep the FTS index up to date.
create trigger trigger_taxon_after_insert after insert on taxon begin
  insert into taxon_fts(rowid, name) values (new.id, new.name);
end;

create trigger trigger_taxon_after_delete after delete on taxon begin
  insert into taxon_fts(taxon_fts, rowid, name) values('delete', old.id, old.name);
end;

create trigger trigger_taxon_after_update after update on taxon begin
  insert into taxon_fts(taxon_fts, rowid, name) values('delete', old.id, old.name);
  insert into taxon_fts(rowid, name) values (new.id, new.name);
end;

-- ============================================================================
-- CONTACT TABLE
-- ============================================================================
create table contact (
  id integer primary key autoincrement,
  name text not null,
  email text,
  address text,
  province text,
  postal_code text,
  country text,
  phone text,
  business text,
  notes text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

create trigger trigger_contact_updated_at after update on contact
begin
  update contact set updated_at = datetime('now') where id = NEW.id;
end;

-- ============================================================================
-- LOCATION TABLE
-- ============================================================================
create table location (
  id integer primary key autoincrement,
  code text not null,
  name text not null,
  description text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

create index location_id_idx on location (id);

create trigger trigger_location_updated_at after update on location
begin
  update location set updated_at = datetime('now') where id = NEW.id;
end;

-- ============================================================================
-- ACCESSION TABLE
-- ============================================================================
create table accession (
  id integer primary key autoincrement,
  code text not null,
  taxon_id integer not null references taxon(id),
  private integer not null default 0 check(private in (0, 1)),
  id_qualifier text check(id_qualifier in (
    'aff',
    'cf',
    'forsan',
    'incorrect',
    'near',
    'questionable'
  )),
  id_qualifier_rank text check(id_qualifier_rank in (
    'below_family',
    'family',
    'genus',
    'species',
    'first_infraspecific_epithet',
    'second_infraspecific_epithet',
    'cultivar'
  )),
  provenance_type text check(provenance_type in (
    'wild',
    'cultivated',
    'not_wild',
    'purchase',
    'insufficient_data'
  )),
  wild_provenance_status text check(wild_provenance_status in (
    'wild_native',
    'wild_non_native',
    'cultivated_native',
    'cultivated',
    'not_wild',
    'purchase',
    'insufficient_data'
  )),
  supplier_contact_id integer references contact(id),
  date_received text,
  date_accessioned text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

create index accession_id_idx on accession (id);

create trigger trigger_accession_updated_at after update on accession
begin
  update accession set updated_at = datetime('now') where id = NEW.id;
end;

-- ============================================================================
-- MATERIAL TABLE
-- ============================================================================
create table material (
  id integer primary key autoincrement,
  code text not null,
  accession_id integer not null references accession(id),
  location_id integer not null references location(id),
  type text not null default 'plant' check(type in ('plant', 'seed', 'vegetative', 'tissue', 'other')),
  status text not null default 'alive' check(status in ('dead', 'alive')),
  memorial integer not null default 0 check(memorial in (0, 1)),
  quantity integer not null default 1,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

create index material_id_idx on material (id);

create trigger trigger_material_updated_at after update on material
begin
  update material set updated_at = datetime('now') where id = NEW.id;
end;

-- ============================================================================
-- MEDIA TABLE
-- ============================================================================
create table media (
  id integer primary key autoincrement,
  s3_bucket text not null,
  s3_key text not null,
  title text null,
  description text null,
  size_in_bytes integer not null,
  media_type text not null,
  created_at text not null default (datetime('now')),
  created_by integer not null references "user"(id),
  updated_at text not null default (datetime('now'))
) strict;

create index media_id_idx on media (id);

create trigger trigger_media_updated_at after update on media
begin
  update media set updated_at = datetime('now') where id = NEW.id;
end;

-- ============================================================================
-- MEDIA_LINK TABLE
-- ============================================================================
create table media_link (
  id integer primary key autoincrement,
  media_id integer not null unique,
  resource_id integer not null,
  resource_type text not null,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

create index media_link_media_id_idx on media_link (media_id);
create index media_link_resource_id_resource_type_idx on media_link (resource_id, resource_type);

create trigger trigger_media_link_updated_at after update on media_link
begin
  update media_link set updated_at = datetime('now') where id = NEW.id;
end;

-- ============================================================================
-- ACTIVITY TABLE (immutable - no updated_at)
-- ============================================================================
create table activity (
  id integer primary key autoincrement,
  data text not null check(json_valid(data)),
  type text not null,
  created_by integer not null references "user"(id),
  created_at text not null default (datetime('now'))
) strict;

create index activity_id_idx on activity (id);
create index activity_created_at_idx on activity (created_at desc);

-- Functional indexes for querying activity by resource ID in JSON data
-- These enable efficient lookups for resource panel activity feeds
create index activity_taxon_id_idx
  on activity (cast(json_extract(data, '$.taxon-id') as integer))
  where json_extract(data, '$.taxon-id') is not null;

create index activity_accession_id_idx
  on activity (cast(json_extract(data, '$.accession-id') as integer))
  where json_extract(data, '$.accession-id') is not null;

create index activity_material_id_idx
  on activity (cast(json_extract(data, '$.material-id') as integer))
  where json_extract(data, '$.material-id') is not null;

create index activity_location_id_idx
  on activity (cast(json_extract(data, '$.location-id') as integer))
  where json_extract(data, '$.location-id') is not null;

create index activity_contact_id_idx
  on activity (cast(json_extract(data, '$.contact-id') as integer))
  where json_extract(data, '$.contact-id') is not null;

-- ============================================================================
-- SETTINGS TABLE (key-value store - no timestamps)
-- ============================================================================
create table settings (
  key text not null unique,
  value text
) strict;

-- ============================================================================
-- COLLECTION TABLE
-- Note: Not using STRICT mode due to SpatiaLite geometry column requirements
-- ============================================================================
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
  geo_coordinates blob,
  geo_uncertainty integer check(geo_uncertainty > 0),
  elevation integer,
  accession_id integer unique constraint collection_accession_id_fkey references accession (id) on delete cascade,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
);

create trigger trigger_collection_updated_at after update on collection
begin
  update collection set updated_at = datetime('now') where id = NEW.id;
end;

-- Register the geometry column with SpatiaLite metadata
-- This enables spatial functions and proper geometry handling
-- Note: Requires SpatiaLite extension to be loaded before running this migration
SELECT AddGeometryColumn('collection', 'geo_coordinates', 4326, 'POINT', 'XY');
