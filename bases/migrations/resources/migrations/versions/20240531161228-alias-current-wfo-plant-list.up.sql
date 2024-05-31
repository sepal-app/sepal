create schema if not exists wfo_plantlist_current;
--;;
create or replace view wfo_plantlist_current.name as
select * from wfo_plantlist_2023_12.name;
--;;
create or replace view wfo_plantlist_current.reference as
select * from wfo_plantlist_2023_12.reference;
--;;
create or replace view wfo_plantlist_current.synonym as
select * from wfo_plantlist_2023_12.synonym;
--;;
create or replace view wfo_plantlist_current.taxon as
select * from wfo_plantlist_2023_12.taxon;
--;;
