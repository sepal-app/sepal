create table contact (
  id integer primary key autoincrement,
  name text not null,
  email text,
  address text,
  province text,
  postal_code text,
  country text,
  phone text,
  business text not null,
  notes text not null
);

alter table accession
add column supplier_contact_id integer references contact(id);

alter table accession
add column date_received integer text;

alter table accession
add column date_accessioned integer text;

