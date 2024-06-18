#!/usr/bin/env bash

set -Eeuxo pipefail

PGPORT=${PGPORT:-5432}
DATABASE_URL="postgres://${PGUSER}:${PGPASSWORD}@${PGHOST}:${PGPORT}/${PGDATABASE}"

dbmate migrate --url "$DATABASE_URL" --no-dump-schema && \
cd projects/app && \
clojure -M:main
