#!/usr/bin/env bash
set -Eeuxo pipefail

# To run this as a different user do `PGUSER=someuser reset_db.sh`

PGUSER=${PGUSER:-sepal}
PGDATABASE=${PGDATABASE:-sepal}
PGPASSWORD="password"
PGPORT=${PGPORT:-5432}
SKIP_WFO_PLANTLIST=${SKIP_WFO_PLANTLIST:-false}

psql -v ON_ERROR_STOP=1 -h localhost -U "$USER" postgres <<-EOSQL
    drop database if exists ${PGDATABASE};
    drop role if exists ${PGUSER};
    create role ${PGUSER} with login password '${PGPASSWORD}';
    create database ${PGDATABASE} owner ${PGUSER};
    grant all privileges on database ${PGDATABASE} to ${PGUSER};
EOSQL

PGUSER=$USER
PGPASSWORD=
DATABASE_URL="postgres://${PGUSER}:${PGPASSWORD}@${PGHOST}:${PGPORT}/${PGDATABASE}?sslmode=disable"

dbmate --url "$DATABASE_URL" load
# dbmate --url "$DATABASE_URL" migrate
if [[ $SKIP_WFO_PLANTLIST != true ]] ; then
   bin/wfo_plantlist_insert.sh;
   bin/wfo_plantlist_to_taxa.sh;
fi
