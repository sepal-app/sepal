#!/usr/bin/env bash

set -Eeuxo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/env.sh"

SEPAL_DATA_HOME=$(get_sepal_data_home)
DB_PATH="$SEPAL_DATA_HOME/sepal.db"

# Ensure directory exists
mkdir -p "$SEPAL_DATA_HOME"

WFO_DATABASE_PATH=${WFO_DATABASE_PATH:-wfo_plantlist_2025-06.db}
MIGRATE_SH=${MIGRATE_SH:-migrate.sh}
SCHEMA_DUMP_FILE=${SCHEMA_DUMP_FILE:-db/schema.sql}

# Remove existing database and SQLite WAL/SHM files
rm -f "$DB_PATH" "$DB_PATH-wal" "$DB_PATH-shm"
sqlite3 "$DB_PATH" <"$SCHEMA_DUMP_FILE"

# Apply any pending migrations
${MIGRATE_SH} apply "$DB_PATH"

#
# Populate the database from the WFO database
#
sqlite3 -cmd "attach database \"${WFO_DATABASE_PATH}\" as wfo;" "$DB_PATH" <<'EOSQL'
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

#
# Create initial admin user if env vars are set
#
if [[ -n "${ADMIN_EMAIL:-}" && -n "${ADMIN_PASSWORD:-}" ]]; then
    echo "Creating admin user..."
    SEPAL_DATA_HOME="$SEPAL_DATA_HOME" clojure -M:dev:cli create-user \
        --email "${ADMIN_EMAIL}" \
        --password "${ADMIN_PASSWORD}" \
        --role admin
fi
