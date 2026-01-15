#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../bin/lib/env.sh"

SEPAL_DATA_HOME=$(get_sepal_data_home)
DB_PATH="$SEPAL_DATA_HOME/sepal.db"

export SEPAL_DATA_HOME
export MIGRATIONS_DIR=db/migrations
export SCHEMA_DUMP_FILE=/dev/null

# Ensure directory and database file exist
mkdir -p "$SEPAL_DATA_HOME"

# First-time deploy: load schema.sql directly (avoids SpatiaLite transaction issues)
# Otherwise just apply any pending migrations
if [[ ! -f "$DB_PATH" ]]; then
    sqlite3 "$DB_PATH" < db/schema.sql
fi
bin/migrate.sh apply "$DB_PATH"

# Ensure SpatiaLite metadata is initialized and geometry column is registered
# (idempotent - safe to run on every startup)
sqlite3 "$DB_PATH" "SELECT InitSpatialMetaData(1);" 2>/dev/null || true
sqlite3 "$DB_PATH" "SELECT RecoverGeometryColumn('collection', 'geo_coordinates', 4326, 'POINT', 'XY');" 2>/dev/null || true &&
    cd projects/app &&
    clojure -M:main
