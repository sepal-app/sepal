alter table accession
add column private integer not null default 0 check(private in (0, 1));

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

