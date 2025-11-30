#!/usr/bin/env bash

set -Eeuxo pipefail

# Determine DATABASE_PATH and DATABASE_URL
if [[ -n "${DATABASE_JDBC_URL:-}" ]]; then
    # Extract from JDBC URL
    DATABASE_PATH=${DATABASE_JDBC_URL//jdbc:sqlite:/}
    DATABASE_URL=${DATABASE_JDBC_URL//jdbc:/}
else
    # Use XDG_DATA_HOME or platform-specific default
    if [[ -n "${XDG_DATA_HOME:-}" ]]; then
        DATA_DIR="$XDG_DATA_HOME"
    elif [[ "$(uname)" == "Darwin" ]]; then
        DATA_DIR="$HOME/Library/Application Support"
    else
        DATA_DIR="$HOME/.local/share"
    fi

    DATABASE_PATH="$DATA_DIR/sepal/sepal.db"
    DATABASE_URL="sqlite:$DATABASE_PATH"

    # Ensure directory exists
    mkdir -p "$(dirname "$DATABASE_PATH")"
fi

WFO_DATABASE_PATH=${WFO_DATABASE_PATH:-wfo_plantlist_2025-06.db}
DBMATE=${DBMATE:-dbmate}

${DBMATE} -u "$DATABASE_URL" drop
${DBMATE} -u "$DATABASE_URL" load

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
