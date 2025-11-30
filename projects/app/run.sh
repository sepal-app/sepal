#!/usr/bin/env bash

set -Eeuo pipefail

# Determine DATABASE_URL for dbmate
if [[ -n "${DATABASE_JDBC_URL:-}" ]]; then
    DATABASE_URL=${DATABASE_JDBC_URL//jdbc:/}
else
    if [[ -n "${XDG_DATA_HOME:-}" ]]; then
        DATA_DIR="$XDG_DATA_HOME"
    elif [[ "$(uname)" == "Darwin" ]]; then
        DATA_DIR="$HOME/Library/Application Support"
    else
        DATA_DIR="$HOME/.local/share"
    fi

    DATABASE_URL="sqlite:$DATA_DIR/sepal/sepal.db"
fi

export DATABASE_URL

bin/dbmate -e DATABASE_URL --no-dump-schema migrate &&
    cd projects/app &&
    clojure -M:main
