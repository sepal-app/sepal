create extension if not exists "uuid-ossp";  -- noqa: RF05
--;;
create extension if not exists plpgsql;
--;;
create extension if not exists pgcrypto;
--;;
create extension if not exists postgis;
--;;
create extension if not exists pg_trgm;
--;;
create table public."user" (
  id int generated by default as identity primary key,
  avatar_public_id text,
  email text not null unique,
  password text not null unique,
  email_verified_at timestamptz default null
);
--;;
create index user_id_idx on public."user" (id);
--;;
create table public.organization (
  id int generated by default as identity primary key,
  abbreviation text,
  name text not null,
  short_name text
);
--;;
create index organization_id_idx on public.organization (id);
--;;
create type organization_role as enum (
  'owner',
  'admin',
  'read',
  'write',
  'guest'
);
--;;
create table public.organization_user (
  organization_id int constraint organization_user_organization_id_fkey references organization (id) not null,
  user_id int constraint organization_user_user_id_fkey references public."user" (id) not null,
  role organization_role not null
);
--;;
alter table public.organization_user
add constraint organization_user_organization_id_user_id_unique unique (organization_id, user_id);
--;;
create or replace function is_organization_member(_organization_id integer, _user_id integer)
returns boolean
language sql
immutable
as $$
  select
    exists (
      select
        1
      from
        organization_user
      where
        user_id = _user_id
        and organization_id = _organization_id
     )
$$;
--;;
create or replace function has_organization_role(
  _organization_id integer, _user_id integer, _roles organization_role []
)
returns boolean
language sql
immutable
as $$
  select
    exists (
      select
        *
      from
        organization_user
      where
        user_id = _user_id
        and organization_id = _organization_id
        and role = any (_roles)
      limit 1)
$$;
--;;
create type public.taxon_rank_enum as enum (
  'class',
  'family',
  'form',
  'genus',
  'kingdom',
  'order',
  'phylum',
  'section',
  'series',
  'species',
  'subclass',
  'subfamily',
  'subform',
  'subgenus',
  'subsection',
  'subseries',
  'subspecies',
  'subtribe',
  'subvariety',
  'superorder',
  'tribe',
  'variety'
);
--;;
create table public.taxon (
  id int generated by default as identity primary key,
  name text not null,
  author text not null default '',
  organization_id int constraint taxon_organization_id_fkey references organization (id) not null,
  parent_id int constraint taxon_parent_id_fkey references taxon (id),
  rank public.taxon_rank_enum not null,
  wfo_plantlist_name_id text,
  constraint taxon_organization_id_wfo_plant_list_name_id_unq unique (organization_id, wfo_plantlist_name_id)
);
--;;
create index taxon_id_idx on public.taxon (id);
--;;
create index taxon_organization_id_idx on public.taxon (organization_id);
--;;
create table public.location (
  id int generated by default as identity primary key,
  code text not null,
  name text not null default '',
  description text not null default '',
  organization_id int constraint location_organization_id_fkey references organization (id) not null
);
--;;
alter table public.location
add constraint location_code_organization_id_key unique (code, organization_id);
--;;
create index location_id_idx on public.location (id);
--;;
create index location_organization_id_idx on public.location (organization_id);
--;;
create table public.accession (
  id int generated by default as identity primary key,
  code text not null,
  taxon_id int constraint accession_taxon_id_fkey references taxon (id) not null,
  organization_id int constraint accession_organization_id_fkey references organization (id) not null
);
--;;
alter table public.accession
add constraint accession_code_organization_id_key unique (code, organization_id);
--;;
create index accession_id_idx on public.accession (id);
--;;
create index accession_organization_id_idx on public.accession (organization_id);
--;;
create table public.material (
  id int generated by default as identity primary key,
  code text not null,
  accession_id int constraint material_accession_id_fkey references accession (id) not null,
  location_id int constraint material_location_id_fkey references location (id) not null,
  organization_id int constraint material_organization_id_fkey references organization (id) not null
);
--;;
alter table public.material
add constraint material_code_accession_id_organization_id_key unique (code, accession_id, organization_id);
--;;
create index material_id_idx on public.material (id);
--;;
create index material_organization_id_idx on public.material (organization_id);
--;;
create table public.media (
  id int not null generated by default as identity primary key,
  s3_bucket text not null,
  s3_key text not null,
  title text null,
  description text null,
  size_in_bytes int not null,
  media_type text not null,
  organization_id int constraint media_organization_id_fkey references organization (id) not null,
  created_at timestamptz not null default now(),
  created_by int constraint media_created_by_fkey references public."user" (id) not null,
  constraint media_organization_id_s3_bucket_s3_key_unq unique (organization_id, s3_bucket, s3_key)
);
--;;
create index media_id_idx on public.media (id);
--;;
create index media_organization_id_idx on public.media (organization_id);
--;;
create table public.activity (
  id int not null generated by default as identity,
  data jsonb not null,
  type text not null,
  organization_id int constraint activity_organization_id_fkey references organization (id) not null,
  created_by int constraint activity_created_by_fkey references public."user" (id) not null,
  created_at timestamptz default timezone('utc'::text, current_timestamp) not null
);
--;;
create index activity_id_idx on public.activity (id);
--;;
create index activity_organization_id_idx on public.activity (organization_id);
--;;