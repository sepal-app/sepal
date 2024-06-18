#!/usr/bin/env bash

set -Eeuo pipefail

PGPORT=${PGPORT:-5432}
DATABASE_URL="postgres://${PGUSER}:${PGPASSWORD}@${PGHOST}:${PGPORT}/${PGDATABASE}?unixSocketPath=${DB_UNIX_SOCKET_PATH}&socketFactory=${DB_SOCKET_FACTORY}&cloudSqlInstance=${CLOUD_SQL_INSTANCE}"

bin/dbmate --no-dump-schema migrate && \
cd projects/app && \
clojure -M:main
