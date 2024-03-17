#!/usr/bin/env bash
set -euo pipefail

# To run this as a different user do `PGUSER=someuser reset_db.sh`

psql -v ON_ERROR_STOP=1 -h localhost postgres <<-EOSQL
    drop database if exists sepal;
    drop role if exists sepal_user;
    create role sepal_user with login password 'password';
    create database sepal owner sepal_user;
    \c sepal;
    create extension if not exists moddatetime;
    create extension if not exists "uuid-ossp";
    create extension if not exists plpgsql;
    create extension if not exists pgcrypto;
    create extension if not exists postgis;
    create extension if not exists pg_trgm;
EOSQL

clojure -X:migrate :profile ${PROFILE:-:local}
