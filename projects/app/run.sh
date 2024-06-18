#!/usr/bin/env bash

set -Eeuo pipefail

PGPORT=${PGPORT:-5432}
DATABASE_URL="postgres://${PGUSER}:${PGPASSWORD}@/${PGDATABASE}?socket=$(dirname ${DB_UNIX_SOCKET_PATH})"

bin/dbmate --url "${DATABASE_URL}" --no-dump-schema migrate && \
cd projects/app && \
clojure -M:main
