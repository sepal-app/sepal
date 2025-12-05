#!/usr/bin/env bash

set -Eeuxo pipefail

# Determine DATABASE_PATH (use env var or default to XDG data dir)
if [[ -z "${DATABASE_PATH:-}" ]]; then
    if [[ -n "${XDG_DATA_HOME:-}" ]]; then
        DATA_DIR="$XDG_DATA_HOME"
    elif [[ "$(uname)" == "Darwin" ]]; then
        DATA_DIR="$HOME/Library/Application Support"
    else
        DATA_DIR="$HOME/.local/share"
    fi
    DATABASE_PATH="$DATA_DIR/Sepal/sepal.db"
fi

# Ensure directory exists
mkdir -p "$(dirname "$DATABASE_PATH")"

WFO_DATABASE_PATH=${WFO_DATABASE_PATH:-wfo_plantlist_2025-06.db}
MIGRATE_SH=${MIGRATE_SH:-migrate.sh}
SCHEMA_DUMP_FILE=${SCHEMA_DUMP_FILE:-db/schema.sql}

# Remove existing database and load schema
rm -f "$DATABASE_PATH"
sqlite3 "$DATABASE_PATH" < "$SCHEMA_DUMP_FILE"

# Apply any pending migrations
${MIGRATE_SH} apply "$DATABASE_PATH"

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
