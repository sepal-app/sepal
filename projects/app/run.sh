#!/usr/bin/env bash

set -Eeuo pipefail

DB_PORT=${DB_PORT:-5432}

DATABASE_URL="postgres://${DB_USER}:${DB_PASSWORD}@/${DB_NAME}?socket=$(dirname ${DB_UNIX_SOCKET_PATH})"
export DATABASE_URL

bin/dbmate -e DATABASE_URL --no-dump-schema migrate &&
    cd projects/app &&
    clojure -M:main
