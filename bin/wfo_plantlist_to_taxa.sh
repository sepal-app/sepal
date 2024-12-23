#!/usr/bin/env bash
#
# This script will use the default psql environment variables for the database
# connection, e.g. PGHOST, PGDATABASE, etc.

set -Eeuxo pipefail

WFO_SCHEMA=${WFO_SCHEMA:-wfo_plantlist_2023_12}
TAXON_FIELD=${TAXON_FIELD:-wfo_taxon_id_2023_12}

# TODO: Add a wfo taxon column or a read only column so that we're required to
# fork wfo taxon to make edits

psql <<EOF
 insert into public.taxon ($TAXON_FIELD, name, author, rank, read_only)
 select
   wfo_t.id $TAXON_FIELD,
   wfo_n.scientific_name name,
   wfo_n.authorship author,
   wfo_n.rank::taxon_rank_enum rank,
   true
 from $WFO_SCHEMA.taxon wfo_t
 join $WFO_SCHEMA.name wfo_n on wfo_n.id = wfo_t.name_id
EOF

psql <<EOF
 update only public.taxon t
 set parent_id = t2.id
 from public.taxon t2, $WFO_SCHEMA.taxon wfo_t
 where t.$TAXON_FIELD = wfo_t.id and t2.$TAXON_FIELD = wfo_t.parent_id;
EOF
