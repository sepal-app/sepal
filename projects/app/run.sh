#!/usr/bin/env bash

set -Eeuo pipefail

# Determine DATABASE_PATH for migrate.sh
if [[ -n "${DATABASE_JDBC_URL:-}" ]]; then
    DATABASE_PATH=${DATABASE_JDBC_URL//jdbc:sqlite:/}
else
    if [[ -n "${XDG_DATA_HOME:-}" ]]; then
        DATA_DIR="$XDG_DATA_HOME"
    elif [[ "$(uname)" == "Darwin" ]]; then
        DATA_DIR="$HOME/Library/Application Support"
    else
        DATA_DIR="$HOME/.local/share"
    fi

    DATABASE_PATH="$DATA_DIR/Sepal/sepal.db"
fi

export DATABASE_PATH
export MIGRATIONS_DIR=db/migrations

# Ensure database directory and file exist, then apply migrations
mkdir -p "$(dirname "$DATABASE_PATH")"
touch "$DATABASE_PATH"
bin/migrate.sh apply "$DATABASE_PATH" &&
    cd projects/app &&
    clojure -M:main
