#!/usr/bin/env bash
set -euo pipefail

# To run this as a different user do `PGUSER=someuser reset_database.sh`

psql -v ON_ERROR_STOP=1 -h localhost postgres <<-EOSQL
    drop database if exists sepal;
    drop role sepal_user;
    create role sepal_user with login password 'password';
    create database sepal owner sepal_user;
    \c sepal;
EOSQL
