#!/usr/bin/env bash
set -euo pipefail

# To run this as a different user do `PGUSER=someuser reset_db.sh`

PGUSER=${PGUSER:-sepal}
PGDATABASE=${PGDATABASE:-sepal}
PGPASSWORD="password"
PROFILE=${PROFILE:-local}

psql -v ON_ERROR_STOP=1 -h localhost postgres <<-EOSQL
    drop database if exists ${PGDATABASE};
    drop role if exists ${PGUSER};
    create role ${PGUSER} with login password '${PGPASSWORD}';
    create database ${PGDATABASE} owner ${PGUSER};
    \c ${PGDATABASE};
    create extension if not exists moddatetime;
    create extension if not exists "uuid-ossp";
    create extension if not exists plpgsql;
    create extension if not exists pgcrypto;
    create extension if not exists postgis;
    create extension if not exists pg_trgm;
EOSQL

# TODO: add  the wfo plant list

clojure -M:migrate migrate -p "${PROFILE}"
