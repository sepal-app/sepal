
-- migrate:up

-- TODO:
-- accession.id_qualifier
-- accession.id_qualifier_rank
-- accession.private
-- accession.provenance
-- accession.wild_provenance_status

-- accession.propagation_id
-- accession.plant_propagation_id
-- accession.collection_id
-- accession.donor_id
--

alter table accession
add column private boolean not null default false;

alter table accession
add column id_qualifier text check(id_qualifier in (
  'aff',
  'cf',
  'forsan',
  'incorrect',
  'near',
  'questionable'
));

alter table accession
add column id_qualifier_rank text check(id_qualifier_rank in (
  'below_family',
  'family',
  'genus',
  'species',
  'first_infraspecific_epithet',
  'second_infraspecific_epithet',
  'cultivar'
));

alter table accession
add column provenance_type text check(provenance_type in (
  'wild',
  'cultivated',
  'not_wild',
  'purchase',
  'insufficient_data'
));

alter table accession
add column wild_provenance_status text check(wild_provenance_status in (
  'wild_native',
  'wild_non_native',
  'cultivated_native',
  'cultivated',
  'not_wild',
  'purchase',
  'insufficient_data'
));

create table source_detail (
  id integer primary key autoincrement,
  name text,
  description text,
  phone text,
  email text,
  address text,
  source_type text check(source_type in (
    'expedition',
    'gene_bank',
    'field_station',
    'staff',
    'university_department',
    'club',
    'municipal_department',
    'commerical',
    'individual',
    'other'
)));

create table source (
  id integer primary key autoincrement,
  sources_code text,
  -- accession_id int constraint source_accession_id_fkey unique references accession (id) not null,
  -- source_detail_id int constraint source_detail_id_fkey references source_detail (id) not null
  accession_id int not null unique references accession (id),
  source_detail_id int not null references source_detail (id)
);


create table collection (
  id integer primary key autoincrement,
  collector text,
  collectors_code text,
  date date,
  locale text,
  -- Always store points as WGS84 but allow setting a coordinate system for conversion
  -- coordinates geography (point, 4326),
  coordinates_accuracy smallint,
  -- Use postgis_srs_all to get all of the coordinate systems
  coordinate_system_srid text,
  altitude smallint,
  altitude_accuracy smallint,
  notes text,
  source_id int not null unique references source (id)
  -- source_id int constraint collection_source_id_fkey unique references source (id) not null
);


-- migrate:down
alter table accession drop column private;
alter table accession drop column id_qualifier;
alter table accession drop column id_qualifier_rank;
alter table accession drop column provenance_type;
alter table accession drop column wild_provenance_status;
drop table collection;
drop table source;
drop table source_detail;
