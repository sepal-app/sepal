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
CREATE TABLE location (
  id integer primary key autoincrement,
  code text not null,
  name text not null,
  description text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
, status text not null default 'active'
  check(status in ('active', 'archived'))) strict;
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
, intended_location_id integer references location(id), received_type text
  REFERENCES accession_received_type(name), quantity_received integer
  CHECK(quantity_received >= 0), propagation_id integer references propagation(id)) strict;
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
CREATE TABLE activity (
  id integer primary key autoincrement,
  data text not null check(json_valid(data)),
  type text not null,
  created_by integer not null references "user"(id),
  created_at text not null default (datetime('now'))
, resource_type text, resource_id integer) strict;
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
, collectors_code text, elevation_accuracy integer) strict;
CREATE INDEX user_id_idx on "user" (id);
CREATE INDEX user_status_idx on "user" (status);
CREATE INDEX user_role_idx on "user" (role);
CREATE INDEX location_id_idx on location (id);
CREATE INDEX accession_id_idx on accession (id);
CREATE INDEX media_id_idx on media (id);
CREATE INDEX activity_id_idx on activity (id);
CREATE INDEX activity_created_at_idx on activity (created_at desc);
CREATE TRIGGER trigger_user_updated_at after update on "user"
begin
  update "user" set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_location_updated_at after update on location
begin
  update location set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_accession_updated_at after update on accession
begin
  update accession set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_media_updated_at after update on media
begin
  update media set updated_at = datetime('now') where id = NEW.id;
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
CREATE TABLE taxon_rank (name text primary key) strict;
CREATE TABLE "taxon" (
  id integer primary key autoincrement,
  name text not null,
  author text,
  parent_id integer references taxon(id),
  rank text not null references taxon_rank(name),
  wfo_taxon_id text,
  vernacular_names text not null default '[]' check(json_valid(vernacular_names)),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
, distribution text) strict;
CREATE INDEX taxon_id_idx on taxon (id);
CREATE INDEX taxon_name_idx on taxon (name);
CREATE INDEX taxon_parent_id_idx on taxon (parent_id);
CREATE INDEX taxon_wfo_taxon_id_idx on taxon (wfo_taxon_id);
CREATE TRIGGER trigger_taxon_updated_at after update on taxon
begin
  update taxon set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TABLE material_status (
  name text primary key
) strict;
CREATE TABLE material_change_reason (
  code text primary key,
  label text not null
) strict;
CREATE TABLE "material" (
  id integer primary key autoincrement,
  code text not null,
  accession_id integer not null references accession(id),
  location_id integer not null references location(id),
  type text not null default 'plant' check(type in ('plant', 'seed', 'vegetative', 'tissue', 'other')),
  status text not null default 'alive' references material_status(name),
  memorial integer not null default 0 check(memorial in (0, 1)),
  quantity integer not null default 1 check(quantity >= 0),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now')), propagation_id integer references propagation(id),
  -- A non-current lot cannot hold material: dead, transferred and other
  -- require quantity 0, while alive, dormant and unknown accept any count.
  check(status in ('alive', 'dormant', 'unknown') or quantity = 0)
) strict;
CREATE INDEX material_id_idx on material (id);
CREATE TRIGGER trigger_material_updated_at after update on material
begin
  update material set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TABLE material_change (
  id integer primary key autoincrement,
  material_id integer not null references material(id) on delete cascade,
  from_location_id integer references location(id),
  to_location_id integer references location(id),
  quantity integer not null,
  reason text references material_change_reason(code),
  changed_at text not null default (datetime('now')),
  note text,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now'))
) strict;
CREATE INDEX material_change_material_id_idx on material_change (material_id);
CREATE INDEX material_change_changed_at_idx on material_change (changed_at desc);
CREATE TABLE taxon_synonym (
  id integer primary key autoincrement,
  taxon_id integer not null references taxon(id),
  synonym_name text not null,
  source text not null default 'local' check(source in ('local', 'imported')),
  created_by integer references "user"(id),
  created_at text not null default (datetime('now'))
) strict;
CREATE INDEX taxon_synonym_taxon_id_idx on taxon_synonym (taxon_id);
CREATE INDEX taxon_synonym_name_idx on taxon_synonym (synonym_name collate nocase);
CREATE TABLE note (
  id integer primary key autoincrement,
  body text not null,
  resource_id integer not null,
  resource_type text not null,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE INDEX note_resource_id_resource_type_idx on note (resource_id, resource_type);
CREATE TRIGGER trigger_note_updated_at after update on note
begin
  update note set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TABLE tag (
  id integer primary key autoincrement,
  name text not null unique collate nocase,
  description text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE TRIGGER trigger_tag_updated_at after update on tag
begin
  update tag set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TABLE tag_link (
  id integer primary key autoincrement,
  tag_id integer not null references tag(id),
  resource_id integer not null,
  resource_type text not null,
  created_at text not null default (datetime('now'))
) strict;
CREATE UNIQUE INDEX tag_link_unique_idx on tag_link (tag_id, resource_id, resource_type);
CREATE INDEX tag_link_resource_id_resource_type_idx on tag_link (resource_id, resource_type);
CREATE INDEX accession_intended_location_id_idx on accession (intended_location_id);
CREATE TABLE accession_received_type (
  name text primary key
) strict;
CREATE INDEX activity_resource_type_resource_id_idx
  on activity (resource_type, resource_id);
CREATE TABLE import_record (
  id integer primary key autoincrement,
  source_table text not null,
  source_id text not null,
  resource_type text not null,
  resource_id integer not null,
  created_at text not null default (datetime('now'))
);
CREATE UNIQUE INDEX import_record_source_table_source_id_idx
  on import_record (source_table, source_id);
CREATE INDEX import_record_resource_type_resource_id_idx
  on import_record (resource_type, resource_id);
CREATE TABLE "media_link" (
  id integer primary key autoincrement,
  media_id integer not null unique references media(id) on delete cascade,
  resource_id integer not null,
  resource_type text not null,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE INDEX media_link_media_id_idx on media_link (media_id);
CREATE INDEX media_link_resource_id_resource_type_idx on media_link (resource_id, resource_type);
CREATE TRIGGER trigger_media_link_updated_at after update on media_link
begin
  update media_link set updated_at = datetime('now') where id = NEW.id;
end;
CREATE UNIQUE INDEX accession_code_idx ON accession (code);
CREATE UNIQUE INDEX material_accession_id_code_idx ON material (accession_id, code);
CREATE VIRTUAL TABLE taxon_fts USING fts5(name, vernacular_names);
CREATE TRIGGER trigger_taxon_after_insert after insert on taxon begin
  insert into taxon_fts(rowid, name, vernacular_names)
  values (new.id,
          new.name,
          (select group_concat(json_extract(value, '$.name'), ' ')
             from json_each(new.vernacular_names)));
end;
CREATE TRIGGER trigger_taxon_after_delete after delete on taxon begin
  delete from taxon_fts where rowid = old.id;
end;
CREATE TRIGGER trigger_taxon_after_update after update on taxon begin
  delete from taxon_fts where rowid = old.id;
  insert into taxon_fts(rowid, name, vernacular_names)
  values (new.id,
          new.name,
          (select group_concat(json_extract(value, '$.name'), ' ')
             from json_each(new.vernacular_names)));
end;
CREATE INDEX location_status_idx on location (status);
CREATE UNIQUE INDEX location_code_idx ON location (code);
CREATE TABLE contact_type (name text primary key) strict;
CREATE TABLE "contact" (
  id integer primary key autoincrement,
  name text not null,
  email text,
  address text,
  address1 text,
  address2 text,
  city text,
  province text,
  postal_code text,
  country text,
  phone text,
  business text,
  type text references contact_type(name),
  notes text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;
CREATE INDEX contact_city_idx on contact (city);
CREATE TRIGGER trigger_contact_updated_at after update on contact
begin
  update contact set updated_at = datetime('now') where id = NEW.id;
end;
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
CREATE TABLE taxon_parentage (
  id integer primary key autoincrement,
  taxon_id integer not null references taxon(id),
  parent_taxon_id integer not null references taxon(id),
  role text not null default 'unknown'
    check(role in ('seed', 'pollen', 'unknown')),
  position integer not null,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  -- No cross means this, and it would loop a renderer that walks the chain.
  check (parent_taxon_id <> taxon_id)
) strict;
CREATE INDEX taxon_parentage_taxon_id_idx on taxon_parentage (taxon_id);
CREATE INDEX taxon_parentage_parent_taxon_id_idx
  on taxon_parentage (parent_taxon_id);
CREATE UNIQUE INDEX taxon_parentage_position_idx
  on taxon_parentage (taxon_id, position);
CREATE TABLE observation_type (
  code text primary key,
  label text not null
) strict;
CREATE TABLE observation_value (
  type text not null references observation_type(code),
  code text not null,
  label text not null,
  primary key (type, code)
) strict;
INSERT INTO observation_value (type, code, label) VALUES
  ('phenology', 'vegetative', 'Vegetative'),
  ('phenology', 'budding', 'Budding'),
  ('phenology', 'flowering', 'Flowering'),
  ('phenology', 'fruiting', 'Fruiting'),
  ('phenology', 'seed_dispersal', 'Seed dispersal'),
  ('phenology', 'senescing', 'Senescing'),
  ('phenology', 'dormant', 'Dormant'),
  ('condition', 'excellent', 'Excellent'),
  ('condition', 'good', 'Good'),
  ('condition', 'fair', 'Fair'),
  ('condition', 'poor', 'Poor'),
  ('condition', 'dying', 'Dying'),
  ('condition', 'dead', 'Dead'),
  ('pest', 'none', 'None'),
  ('pest', 'light', 'Light'),
  ('pest', 'moderate', 'Moderate'),
  ('pest', 'severe', 'Severe'),
  ('disease', 'none', 'None'),
  ('disease', 'light', 'Light'),
  ('disease', 'moderate', 'Moderate'),
  ('disease', 'severe', 'Severe');
CREATE TABLE observation (
  id integer primary key autoincrement,
  resource_id integer not null,
  resource_type text not null,
  type text not null references observation_type(code),
  value text,
  observed_on text not null,
  observed_by text,
  next_check_on text,
  note text,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now')),
  foreign key (type, value) references observation_value(type, code)
) strict;
CREATE INDEX observation_resource_id_resource_type_idx
  on observation (resource_id, resource_type);
CREATE INDEX observation_type_observed_on_idx on observation (type, observed_on);
CREATE INDEX observation_next_check_on_idx on observation (next_check_on);
CREATE TRIGGER trigger_observation_updated_at after update on observation
begin
  update observation set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TABLE propagation_type (
  name text primary key,
  label text not null,
  -- 1 when the method yields the parent genotype, 0 when it does not, null
  -- when the method does not decide.
  clonal integer check(clonal in (0, 1))
) strict;
CREATE TABLE propagation_status (
  name text primary key,
  label text not null
) strict;
CREATE TABLE propagation (
  id integer primary key autoincrement,
  type text not null references propagation_type(name),
  status text not null default 'active' references propagation_status(name),
  parent_accession_id integer not null references accession(id),
  parent_material_id integer references material(id),
  rootstock_taxon_id integer references taxon(id),
  -- Where the batch physically sits while it runs. A nursery bench is a
  -- location like any other, and archiving handles one that goes away.
  location_id integer references location(id),
  propagated_on text,
  succeeded_on text,
  -- Propagules started, and how many came through: germinated for seed,
  -- struck for cuttings, taken for grafts. Null means uncounted, which is the
  -- normal case for a mass sowing -- and recording a mass sowing as null
  -- rather than as an estimate is what makes the comparison below safe.
  -- Either side being null passes it.
  quantity_started integer check(quantity_started >= 0),
  quantity_succeeded integer check(quantity_succeeded >= 0)
    check(quantity_succeeded <= quantity_started),
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
, notes text) strict;
CREATE INDEX propagation_parent_accession_id_idx
  on propagation (parent_accession_id);
CREATE INDEX propagation_parent_material_id_idx
  on propagation (parent_material_id);
CREATE INDEX propagation_rootstock_taxon_id_idx
  on propagation (rootstock_taxon_id);
CREATE INDEX propagation_location_id_idx on propagation (location_id);
CREATE INDEX propagation_status_idx on propagation (status);
CREATE TRIGGER trigger_propagation_updated_at after update on propagation
begin
  update propagation set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_propagation_parent_material_insert
before insert on propagation
when NEW.parent_material_id is not null
begin
  select raise(abort, 'parent_material_id must belong to parent_accession_id')
  where not exists (
    select 1 from material
    where id = NEW.parent_material_id
      and accession_id = NEW.parent_accession_id
  );
end;
CREATE TRIGGER trigger_propagation_parent_material_update
before update on propagation
when NEW.parent_material_id is not null
begin
  select raise(abort, 'parent_material_id must belong to parent_accession_id')
  where not exists (
    select 1 from material
    where id = NEW.parent_material_id
      and accession_id = NEW.parent_accession_id
  );
end;
CREATE INDEX material_propagation_id_idx on material (propagation_id);
CREATE INDEX accession_propagation_id_idx on accession (propagation_id);
INSERT INTO accession_received_type VALUES('air_layer');
INSERT INTO accession_received_type VALUES('balled_and_burlapped');
INSERT INTO accession_received_type VALUES('bare_root_plant');
INSERT INTO accession_received_type VALUES('bud_cutting');
INSERT INTO accession_received_type VALUES('budded');
INSERT INTO accession_received_type VALUES('bulb');
INSERT INTO accession_received_type VALUES('bulbil');
INSERT INTO accession_received_type VALUES('clump');
INSERT INTO accession_received_type VALUES('corm');
INSERT INTO accession_received_type VALUES('division');
INSERT INTO accession_received_type VALUES('graft');
INSERT INTO accession_received_type VALUES('layer');
INSERT INTO accession_received_type VALUES('plant');
INSERT INTO accession_received_type VALUES('pseudobulb');
INSERT INTO accession_received_type VALUES('rhizome');
INSERT INTO accession_received_type VALUES('root');
INSERT INTO accession_received_type VALUES('root_cutting');
INSERT INTO accession_received_type VALUES('root_sucker');
INSERT INTO accession_received_type VALUES('rooted_cutting');
INSERT INTO accession_received_type VALUES('scion');
INSERT INTO accession_received_type VALUES('seed');
INSERT INTO accession_received_type VALUES('seedling');
INSERT INTO accession_received_type VALUES('spore');
INSERT INTO accession_received_type VALUES('sporeling');
INSERT INTO accession_received_type VALUES('tuber');
INSERT INTO accession_received_type VALUES('unknown');
INSERT INTO accession_received_type VALUES('unrooted_cutting');
INSERT INTO accession_received_type VALUES('vegetative_spreading');
INSERT INTO contact_type VALUES('arboretum');
INSERT INTO contact_type VALUES('botanic_garden');
INSERT INTO contact_type VALUES('club');
INSERT INTO contact_type VALUES('commercial');
INSERT INTO contact_type VALUES('expedition');
INSERT INTO contact_type VALUES('gene_bank');
INSERT INTO contact_type VALUES('individual');
INSERT INTO contact_type VALUES('municipal_department');
INSERT INTO contact_type VALUES('nursery');
INSERT INTO contact_type VALUES('other');
INSERT INTO contact_type VALUES('research_station');
INSERT INTO contact_type VALUES('seed_bank');
INSERT INTO contact_type VALUES('staff');
INSERT INTO contact_type VALUES('university_department');
INSERT INTO contact_type VALUES('unknown');
INSERT INTO material_change_reason VALUES('dead','Dead');
INSERT INTO material_change_reason VALUES('discarded','Discarded');
INSERT INTO material_change_reason VALUES('discarded_weedy','Discarded, weedy');
INSERT INTO material_change_reason VALUES('lost','Lost, whereabouts unknown');
INSERT INTO material_change_reason VALUES('stolen','Stolen');
INSERT INTO material_change_reason VALUES('winter_kill','Winter kill');
INSERT INTO material_change_reason VALUES('summer_kill','Summer kill');
INSERT INTO material_change_reason VALUES('error_correction','Error correction');
INSERT INTO material_change_reason VALUES('distributed','Distributed elsewhere');
INSERT INTO material_change_reason VALUES('deleted','Deleted, year dead unknown');
INSERT INTO material_change_reason VALUES('did_not_germinate','Did not germinate');
INSERT INTO material_change_reason VALUES('discarded_seedling','Discarded seedling');
INSERT INTO material_change_reason VALUES('given_away','Given away');
INSERT INTO material_change_reason VALUES('transferred','Transferred elsewhere');
INSERT INTO material_change_reason VALUES('other','Other');
INSERT INTO material_change_reason VALUES('divided','Divided');
INSERT INTO material_status VALUES('alive');
INSERT INTO material_status VALUES('dead');
INSERT INTO material_status VALUES('dormant');
INSERT INTO material_status VALUES('transferred');
INSERT INTO material_status VALUES('other');
INSERT INTO material_status VALUES('unknown');
INSERT INTO observation_type VALUES('phenology','Phenology');
INSERT INTO observation_type VALUES('condition','Condition');
INSERT INTO observation_type VALUES('pest','Pest');
INSERT INTO observation_type VALUES('disease','Disease');
INSERT INTO observation_type VALUES('general','General');
INSERT INTO propagation_status VALUES('active','In progress');
INSERT INTO propagation_status VALUES('complete','Complete');
INSERT INTO propagation_status VALUES('failed','Failed');
INSERT INTO propagation_type VALUES('seed','Seed',0);
INSERT INTO propagation_type VALUES('cutting','Cutting',1);
INSERT INTO propagation_type VALUES('division','Division',1);
INSERT INTO propagation_type VALUES('graft','Graft',1);
INSERT INTO propagation_type VALUES('layering','Layering',1);
INSERT INTO propagation_type VALUES('tissue_culture','Tissue culture',1);
INSERT INTO propagation_type VALUES('other','Other',NULL);
INSERT INTO taxon_rank VALUES('aggregate');
INSERT INTO taxon_rank VALUES('class');
INSERT INTO taxon_rank VALUES('convariety');
INSERT INTO taxon_rank VALUES('cultivar');
INSERT INTO taxon_rank VALUES('family');
INSERT INTO taxon_rank VALUES('form');
INSERT INTO taxon_rank VALUES('genus');
INSERT INTO taxon_rank VALUES('grex');
INSERT INTO taxon_rank VALUES('group');
INSERT INTO taxon_rank VALUES('kingdom');
INSERT INTO taxon_rank VALUES('lusus');
INSERT INTO taxon_rank VALUES('order');
INSERT INTO taxon_rank VALUES('phylum');
INSERT INTO taxon_rank VALUES('prole');
INSERT INTO taxon_rank VALUES('section');
INSERT INTO taxon_rank VALUES('series');
INSERT INTO taxon_rank VALUES('species');
INSERT INTO taxon_rank VALUES('subclass');
INSERT INTO taxon_rank VALUES('subfamily');
INSERT INTO taxon_rank VALUES('subform');
INSERT INTO taxon_rank VALUES('subgenus');
INSERT INTO taxon_rank VALUES('subkingdom');
INSERT INTO taxon_rank VALUES('suborder');
INSERT INTO taxon_rank VALUES('subphylum');
INSERT INTO taxon_rank VALUES('subsection');
INSERT INTO taxon_rank VALUES('subseries');
INSERT INTO taxon_rank VALUES('subspecies');
INSERT INTO taxon_rank VALUES('subtribe');
INSERT INTO taxon_rank VALUES('subvariety');
INSERT INTO taxon_rank VALUES('superclass');
INSERT INTO taxon_rank VALUES('superfamily');
INSERT INTO taxon_rank VALUES('superorder');
INSERT INTO taxon_rank VALUES('supertribe');
INSERT INTO taxon_rank VALUES('tribe');
INSERT INTO taxon_rank VALUES('unranked');
INSERT INTO taxon_rank VALUES('variety');
INSERT INTO settings VALUES('codes.accession_strict','0');
INSERT INTO settings VALUES('codes.accession_template','{year}.{seq:0000}');
INSERT INTO settings VALUES('codes.material_separator','.');
INSERT INTO settings VALUES('codes.material_strict','0');
INSERT INTO settings VALUES('codes.material_template','{seq}');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20251213120000', '2025-12-13 13:29:08');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260113120000', '2026-01-13 12:00:00');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260831120000', '2026-08-31 12:00:00');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260901153000', '2026-09-01 15:30:00');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260902120000', '2026-09-01 22:46:08');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260902160000', '2026-09-02 20:51:12');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260903120000', '2026-09-05 17:51:02');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260906120000', '2026-09-06 16:55:14');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260906130000', '2026-09-06 17:47:59');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260907120000', '2026-09-07 14:31:51');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260907140000', '2026-09-08 00:14:02');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260909120000', '2026-09-09 23:52:51');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260913120000', '2026-09-13 17:54:36');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260913130000', '2026-09-13 18:38:31');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260914120000', '2026-09-15 01:13:12');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260916120000', '2026-09-16 13:01:15');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260916140000', '2026-09-16 20:09:46');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260916150000', '2026-09-16 20:09:46');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260917120000', '2026-09-17 15:05:28');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260917130000', '2026-09-17 15:34:45');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260917140000', '2026-09-17 17:00:44');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260920120000', '2026-09-20 19:46:19');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260920130000', '2026-09-21 13:44:07');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260924120000', '2026-09-24 12:00:00');
INSERT INTO "schema_version" (version, applied_at) VALUES ('20260924130000', '2026-09-24 13:00:00');
