-- migrate:up
alter table public.taxon
add column vernacular_names jsonb default '[]'::jsonb not null ;

-- migrate:down
alter table public.taxon
drop column vernacular_names;
