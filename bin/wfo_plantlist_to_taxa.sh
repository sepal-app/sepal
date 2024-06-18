#!/usr/bin/env bash

set -Eeuxo pipefail

WFO_SCHEMA=${WFO_SCHEMA:-wfo_plantlist_2023_12}
TAXON_FIELD=${TAXON_FIELD:-wfo_taxon_id_2023_12}

PGHOST=localhost
PGDATABASE=sepal

psql <<EOF
 insert into public.taxon ($TAXON_FIELD, name, author, rank)
 select
   wfo_t.id $TAXON_FIELD,
   wfo_n.scientific_name name,
   wfo_n.authorship author,
   wfo_n.rank::taxon_rank_enum rank
 from $WFO_SCHEMA.taxon wfo_t
 join $WFO_SCHEMA.name wfo_n on wfo_n.id = wfo_t.name_id
EOF

psql <<EOF
 update only public.taxon t
 set parent_id = t2.id
 from public.taxon t2, $WFO_SCHEMA.taxon wfo_t
 where t.$TAXON_FIELD = wfo_t.id and t2.$TAXON_FIELD = wfo_t.parent_id;
EOF
