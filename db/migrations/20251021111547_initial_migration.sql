pragma foreign_keys = on;

-- ============================================================================
-- USER TABLE
-- ============================================================================
create table "user" (
  id integer primary key autoincrement,
  avatar_public_id text,
  email text not null unique,
  password text not null unique,
  email_verified_at text default null
);

create index user_id_idx on "user" (id);

-- ============================================================================
-- TAXON TABLE
-- ============================================================================
-- Note: taxon_rank is text with check constraint (replaces enum)
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
  read_only integer not null default 0,
  vernacular_names text not null default '[]' check(json_valid(vernacular_names))
);

create index taxon_id_idx on taxon (id);
create index taxon_name_idx on taxon (name);
-- Note: Removed taxon_name_trgm_idx (gist not supported in SQLite)
create index taxon_parent_id_idx on taxon (parent_id);
create index taxon_wfo_taxon_id_idx on taxon (wfo_taxon_id);

-- Taxon full text search

create virtual table taxon_fts using fts5(name, content='taxon', content_rowid='id'); --, tokenize="porter");

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
-- LOCATION TABLE
-- ============================================================================
create table location (
  id integer primary key autoincrement,
  code text not null,
  name text not null default '',
  description text not null default ''
);

create index location_id_idx on location (id);

-- ============================================================================
-- ACCESSION TABLE
-- ============================================================================
create table accession (
  id integer primary key autoincrement,
  code text not null,
  taxon_id integer not null references taxon(id)
);

create index accession_id_idx on accession (id);

-- ============================================================================
-- MATERIAL TABLE
-- ============================================================================
-- Note: material type and status are text with check constraints (replace enums)
create table material (
  id integer primary key autoincrement,
  code text not null,
  accession_id integer not null references accession(id),
  location_id integer not null references location(id),
  type text not null default 'plant' check(type in ('plant', 'seed', 'vegetative', 'tissue', 'other')),
  status text not null default 'alive' check(status in ('dead', 'alive')),
  memorial integer not null default 0,
  quantity integer not null default 1
);

create index material_id_idx on material (id);

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
  created_by integer not null references "user"(id)
);

create index media_id_idx on media (id);

-- ============================================================================
-- MEDIA_LINK TABLE
-- ============================================================================
create table media_link (
  id integer primary key autoincrement,
  media_id integer not null unique,
  resource_id integer not null,
  resource_type text not null
);

create index media_link_media_id_idx on media_link (media_id);
create index media_link_resource_id_resource_type_idx on media_link (resource_id, resource_type);

-- ============================================================================
-- ACTIVITY TABLE
-- ============================================================================
create table activity (
  id integer primary key autoincrement,
  data text not null check(json_valid(data)),
  type text not null,
  created_by integer not null references "user"(id),
  created_at text not null default (datetime('now'))
);

create index activity_id_idx on activity (id);

-- ============================================================================
-- SETTINGS TABLE
-- ============================================================================
create table settings (
  key text not null,
  value text,
  user_id integer references "user"(id)
);

-- ============================================================================
-- WFO PLANTLIST TABLES - REMOVED
-- ============================================================================
-- Note: All WFO (World Flora Online) tables have been removed.
-- If WFO integration is needed in the future, it can be added separately.

