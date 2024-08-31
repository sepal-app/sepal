-- migrate:up
create type public.material_type_enum as enum (
  'plant',
  'seed',
  'vegetative',
  'tissue',
  'other'
);

create type public.material_status_enum as enum (
  'dead',
  'alive'
);

alter table public.material
add column type public.material_type_enum not null default 'plant',
add column status public.material_status_enum not null default 'alive',
add column memorial boolean not null default false,
add column quantity integer not null default 1;

-- migrate:down
alter table public.material
drop column type,
drop column status,
drop column memorial,
drop column quantity;

drop type public.material_type_enum;
drop type public.material_status_enum;
