#!/usr/bin/env bash
set -Eeuxo pipefail

DBUSER=${DBUSER:-${USER}}
DBNAME=${DBNAME:-sepal}
PORT=${PORT:-5432}

psql -v ON_ERROR_STOP=1 --username ${USER} -p ${PORT} -h localhost postgres <<-EOSQL
    drop database if exists ${DBNAME};
    create database ${DBNAME};
EOSQL


if [[ ${USER} != ${DBUSER} ]] ; then
psql -v ON_ERROR_STOP=1 --username ${USER} -p ${PORT} -h localhost postgres <<-EOSQL
    drop role if exists ${DBUSER};
    create role ${DBUSER} with login;
    grant all on database ${DBNAME} to ${DBUSER};
EOSQL
fi


psql -v ON_ERROR_STOP=1 --username ${USER} -p ${PORT} -h localhost ${DBNAME} <<-EOSQL
    create extension if not exists "uuid-ossp";
    create extension if not exists "pg_trgm";
    create extension if not exists plpgsql;
    create extension if not exists pgcrypto;
    create extension if not exists moddatetime;
EOSQL

flask db upgrade
