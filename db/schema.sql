CREATE TABLE "schema_version" (version TEXT NOT NULL, applied_at TEXT DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE "user" (
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
  vernacular_names text not null default '[]' check(json_valid(vernacular_names)),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE VIRTUAL TABLE taxon_fts using fts5(name, content='taxon', content_rowid='id');
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
  notes text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE TABLE location (
  id integer primary key autoincrement,
  code text not null,
  name text not null,
  description text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE TABLE accession (
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
CREATE TABLE material (
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
CREATE TABLE media (
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
CREATE TABLE media_link (
  id integer primary key autoincrement,
  media_id integer not null unique,
  resource_id integer not null,
  resource_type text not null,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE TABLE activity (
  id integer primary key autoincrement,
  data text not null check(json_valid(data)),
  type text not null,
  created_by integer not null references "user"(id),
  created_at text not null default (datetime('now'))
) strict;
CREATE TABLE settings (
  key text not null unique,
  value text
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
  geo_coordinates blob,
  geo_uncertainty integer check(geo_uncertainty > 0),
  elevation integer,
  accession_id integer unique constraint collection_accession_id_fkey references accession (id) on delete cascade,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE INDEX user_id_idx on "user" (id);
CREATE INDEX user_status_idx on "user" (status);
CREATE INDEX user_role_idx on "user" (role);
CREATE INDEX taxon_id_idx on taxon (id);
CREATE INDEX taxon_name_idx on taxon (name);
CREATE INDEX taxon_parent_id_idx on taxon (parent_id);
CREATE INDEX taxon_wfo_taxon_id_idx on taxon (wfo_taxon_id);
CREATE INDEX location_id_idx on location (id);
CREATE INDEX accession_id_idx on accession (id);
CREATE INDEX material_id_idx on material (id);
CREATE INDEX media_id_idx on media (id);
CREATE INDEX media_link_media_id_idx on media_link (media_id);
CREATE INDEX media_link_resource_id_resource_type_idx on media_link (resource_id, resource_type);
CREATE INDEX activity_id_idx on activity (id);
CREATE INDEX activity_created_at_idx on activity (created_at desc);
CREATE TRIGGER trigger_user_updated_at after update on "user"
begin
  update "user" set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_taxon_updated_at after update on taxon
begin
  update taxon set updated_at = datetime('now') where id = NEW.id;
end;
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
CREATE TRIGGER trigger_contact_updated_at after update on contact
begin
  update contact set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_location_updated_at after update on location
begin
  update location set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_accession_updated_at after update on accession
begin
  update accession set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_material_updated_at after update on material
begin
  update material set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_media_updated_at after update on media
begin
  update media set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_media_link_updated_at after update on media_link
begin
  update media_link set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_collection_updated_at after update on collection
begin
  update collection set updated_at = datetime('now') where id = NEW.id;
end;
CREATE VIRTUAL TABLE accession_fts USING fts5(
  code,
  content='accession',
  content_rowid='id'
);
CREATE TRIGGER trigger_accession_fts_insert AFTER INSERT ON accession BEGIN
  INSERT INTO accession_fts(rowid, code) VALUES (new.id, new.code);
END;
CREATE TRIGGER trigger_accession_fts_delete AFTER DELETE ON accession BEGIN
  INSERT INTO accession_fts(accession_fts, rowid, code) VALUES('delete', old.id, old.code);
END;
CREATE TRIGGER trigger_accession_fts_update AFTER UPDATE OF code ON accession BEGIN
  INSERT INTO accession_fts(accession_fts, rowid, code) VALUES('delete', old.id, old.code);
  INSERT INTO accession_fts(rowid, code) VALUES (new.id, new.code);
END;
CREATE VIRTUAL TABLE location_fts USING fts5(
  code,
  name,
  description,
  content='location',
  content_rowid='id'
);
CREATE TRIGGER trigger_location_fts_insert AFTER INSERT ON location BEGIN
  INSERT INTO location_fts(rowid, code, name, description)
  VALUES (new.id, new.code, new.name, COALESCE(new.description, ''));
END;
CREATE TRIGGER trigger_location_fts_delete AFTER DELETE ON location BEGIN
  INSERT INTO location_fts(location_fts, rowid, code, name, description)
  VALUES('delete', old.id, old.code, old.name, COALESCE(old.description, ''));
END;
CREATE TRIGGER trigger_location_fts_update AFTER UPDATE OF code, name, description ON location BEGIN
  INSERT INTO location_fts(location_fts, rowid, code, name, description)
  VALUES('delete', old.id, old.code, old.name, COALESCE(old.description, ''));
  INSERT INTO location_fts(rowid, code, name, description)
  VALUES (new.id, new.code, new.name, COALESCE(new.description, ''));
END;
CREATE VIRTUAL TABLE contact_fts USING fts5(
  name,
  business,
  email,
  content='contact',
  content_rowid='id'
);
CREATE TRIGGER trigger_contact_fts_insert AFTER INSERT ON contact BEGIN
  INSERT INTO contact_fts(rowid, name, business, email)
  VALUES (new.id, new.name, COALESCE(new.business, ''), COALESCE(new.email, ''));
END;
CREATE TRIGGER trigger_contact_fts_delete AFTER DELETE ON contact BEGIN
  INSERT INTO contact_fts(contact_fts, rowid, name, business, email)
  VALUES('delete', old.id, old.name, COALESCE(old.business, ''), COALESCE(old.email, ''));
END;
CREATE TRIGGER trigger_contact_fts_update AFTER UPDATE OF name, business, email ON contact BEGIN
  INSERT INTO contact_fts(contact_fts, rowid, name, business, email)
  VALUES('delete', old.id, old.name, COALESCE(old.business, ''), COALESCE(old.email, ''));
  INSERT INTO contact_fts(rowid, name, business, email)
  VALUES (new.id, new.name, COALESCE(new.business, ''), COALESCE(new.email, ''));
END;
INSERT INTO "schema_version" (version, applied_at) VALUES ('20251213120000', '2025-12-13 13:29:08');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260113120000', '2026-01-13 12:00:00');
