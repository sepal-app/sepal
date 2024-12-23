-- migrate:up
drop table organization_user;

drop type organization_role;

alter table location drop column organization_id;

alter table material drop column organization_id;

alter table accession drop column organization_id;

alter table media drop column organization_id;

alter table activity drop column organization_id;

alter table taxon
add column read_only boolean not null default false;

update taxon
set read_only = true
where organization_id is null;

alter table taxon
drop column organization_id;

drop table organization;

create table settings (
  key text not null,
  value text,
  user_id int constraint user_id_fkey references public.user (id)
);
-- migrate:down
