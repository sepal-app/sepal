#!/usr/bin/env bash

set -Eeuxo pipefail

DATABASE_URL=${DATABASE_JDBC_URL//jdbc:/}
DATABASE_PATH=${DATABASE_JDBC_URL//jdbc:sqlite:/}
WFO_DATABASE_PATH=${WFO_DATABASE_PATH:-wfo_plantlist_2025-06.db}

dbmate -u "$DATABASE_URL" drop
dbmate -u "$DATABASE_URL" load

#
# Populate the database from the WFO database
#
sqlite3 -cmd "attach database \"${WFO_DATABASE_PATH}\" as wfo;" "$DATABASE_PATH" <<'EOSQL'
 insert into taxon (wfo_taxon_id, name, author, rank, read_only)
 select
   wfo_t.ID wfo_taxon_id,
   wfo_n.scientificName name,
   wfo_n.authorship author,
   wfo_n.rank rank,
   true
 from wfo.taxon wfo_t
 join wfo.name wfo_n on wfo_n.ID = wfo_t.nameID
EOSQL
