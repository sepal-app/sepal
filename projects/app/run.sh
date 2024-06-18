#!/usr/bin/env bash

set -Eeuo pipefail

PGPORT=${PGPORT:-5432}
DATABASE_URL="postgres://${PGUSER}:${PGPASSWORD}@${PGHOST}:${PGPORT}/${PGDATABASE}"

bin/dbmate --no-dump-schema migrate && \
cd projects/app && \
clojure -M:main
