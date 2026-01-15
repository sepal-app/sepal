#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../bin/lib/env.sh"

SEPAL_DATA_HOME=$(get_sepal_data_home)
DB_PATH="$SEPAL_DATA_HOME/sepal.db"

export SEPAL_DATA_HOME
export MIGRATIONS_DIR=db/migrations
export SCHEMA_DUMP_FILE=/dev/null

# Ensure directory and database file exist, then apply migrations
mkdir -p "$SEPAL_DATA_HOME"
touch "$DB_PATH"
bin/migrate.sh apply "$DB_PATH" &&
    cd projects/app &&
    clojure -M:main
