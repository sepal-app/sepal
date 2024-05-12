drop schema if exists wfo_plantlist_2023_12 cascade;

create schema wfo_plantlist_2023_12;

set schema 'wfo_plantlist_2023_12';

create table name (
    id text,
    alternative_id text,
    basionym_id text,
    scientific_name text,
    authorship text,
    rank text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    code text,
    reference_id text,
    published_in_year text,
    link text
);

\copy name(id, alternative_id, basionym_id, scientific_name, authorship, rank, uninomial, genus, infrageneric_epithet, specific_epithet, infraspecific_epithet, code, reference_id, published_in_year, link) from 'name.tsv' with (format csv, header true, delimiter E'\t');

create index name_id_idx on name (id);
create index name_scientific_name_trgm_idx on name using gin (scientific gin_trgm_ops);

create table reference (
  id text,
  citation text,
  link text,
  doi text,
  remarks text
);


\copy reference (id, citation, link, doi, remarks) from 'reference.tsv' with (format csv, header true, delimiter E'\t');

create index reference_id_idx on reference (id);

create table synonym (
    id text,
    taxon_id text,
    name_id text,
    according_to_id text,
    reference_id text,
    link text
);

\copy synonym (id, taxon_id, name_id, according_to_id, reference_id, link) from 'synonym.tsv' with (format csv, header true, delimiter E'\t');

create index synonym_id_idx on synonym (id);

create table taxon (
    id text,
    -- TODO: Add a foreign key relation between taxon.name_id and name.id?
    name_id text,
    parent_id text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_id text,
    -- TODO:  Parse and store as timestamptz?
    scrutinizer_date text,
    reference_id text,
    link text
);

\COPY taxon(id, name_id, parent_id, according_to_id, scrutinizer, scrutinizer_id, scrutinizer_date, reference_id, link) from 'taxon.tsv' with (format csv, header true, delimiter E'\t');

create index taxon_id_idx on taxon (id);
create index taxon_name_id_idx on taxon(name_id);
create index taxon_parent_id_idx on taxon(parent_id);
