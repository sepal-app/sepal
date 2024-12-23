#!/usr/bin/env bash
set -Eeuxo pipefail

# To run this as a different user do `USER=someuser reset_db.sh`
#
# To run this against the test database do:
# DATABASE_URL=$TEST_DATABASE_URL DB_NAME=sepal_test DB_USER=sepal_test bin/reset-db.sh

DB_USER=${DB_USER:-sepal}
DB_NAME=${DB_NAME:-sepal}
DB_PASSWORD="password"
DB_PORT=${DB_PORT:-5432}
SKIP_WFO_PLANTLIST=${SKIP_WFO_PLANTLIST:-false}

psql -v ON_ERROR_STOP=1 -h localhost -U "$USER" postgres <<-EOSQL
    drop database if exists ${DB_NAME};
    drop role if exists ${DB_USER};
    create role ${DB_USER} with login superuser password '${DB_PASSWORD}';
    create database ${DB_NAME} owner ${DB_USER};
    grant all privileges on database ${DB_NAME} to ${DB_USER};
EOSQL

dbmate -e DATABASE_URL load

if [[ $SKIP_WFO_PLANTLIST != true ]]; then
    PGHOST=localhost PGDATABASE=$DB_NAME bin/wfo_plantlist_insert.sh
    PGHOST=localhost PGDATABASE=$DB_NAME bin/wfo_plantlist_to_taxa.sh
fi
