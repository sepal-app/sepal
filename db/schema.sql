CREATE TABLE IF NOT EXISTS "schema_migrations" (version varchar(128) primary key);
CREATE TABLE IF NOT EXISTS "user" (
  id integer primary key autoincrement,
  avatar_public_id text,
  email text not null unique,
  password text not null unique,
  email_verified_at text default null
);
CREATE INDEX user_id_idx on "user" (id);
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
  read_only integer not null default 0,
  vernacular_names text not null default '[]' check(json_valid(vernacular_names))
);
CREATE INDEX taxon_id_idx on taxon (id);
CREATE INDEX taxon_name_idx on taxon (name);
CREATE INDEX taxon_parent_id_idx on taxon (parent_id);
CREATE INDEX taxon_wfo_taxon_id_idx on taxon (wfo_taxon_id);
CREATE TABLE location (
  id integer primary key autoincrement,
  code text not null,
  name text not null default '',
  description text not null default ''
);
CREATE INDEX location_id_idx on location (id);
CREATE TABLE accession (
  id integer primary key autoincrement,
  code text not null,
  taxon_id integer not null references taxon(id)
);
CREATE INDEX accession_id_idx on accession (id);
CREATE TABLE material (
  id integer primary key autoincrement,
  code text not null,
  accession_id integer not null references accession(id),
  location_id integer not null references location(id),
  type text not null default 'plant' check(type in ('plant', 'seed', 'vegetative', 'tissue', 'other')),
  status text not null default 'alive' check(status in ('dead', 'alive')),
  memorial integer not null default 0,
  quantity integer not null default 1
);
CREATE INDEX material_id_idx on material (id);
CREATE TABLE media (
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
CREATE INDEX media_id_idx on media (id);
CREATE TABLE media_link (
  id integer primary key autoincrement,
  media_id integer not null unique,
  resource_id integer not null,
  resource_type text not null
);
CREATE INDEX media_link_media_id_idx on media_link (media_id);
CREATE INDEX media_link_resource_id_resource_type_idx on media_link (resource_id, resource_type);
CREATE TABLE activity (
  id integer primary key autoincrement,
  data text not null check(json_valid(data)),
  type text not null,
  created_by integer not null references "user"(id),
  created_at text not null default (datetime('now'))
);
CREATE INDEX activity_id_idx on activity (id);
CREATE TABLE settings (
  key text not null,
  value text,
  user_id integer references "user"(id)
);
-- Dbmate schema migrations
INSERT INTO "schema_migrations" (version) VALUES
  ('20251021111547');
