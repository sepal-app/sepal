#!/usr/bin/env bash
# Regenerate components/database/resources/database/schema.sql from a migrated
# database.
#
# schema.sql is the baseline provision! loads and bin/reset-db.sh applies before
# migrating, so it has to match what the migrations produce. It is `.schema`
# rather than `.dump`: a dump emits the FTS5 shadow tables and toggles
# writable_schema, neither of which belongs in a baseline.
#
#   usage: bin/dump-schema.sh <db-path> > components/database/resources/database/schema.sql
set -Eeuo pipefail
DB="$1"
sqlite3 "file:$DB?mode=ro" ".schema" \
  | sed -e 's/^CREATE TABLE IF NOT EXISTS /CREATE TABLE /' \
  | grep -v "^CREATE TABLE sqlite_sequence(name,seq);$" \
  | grep -vE "^CREATE TABLE '[a-z_]+_fts_(data|idx|docsize|config)'" \
  | perl -0pe "s{\n/\* [a-z_]+\([a-z_,]+\) \*/;}{;}g"
sqlite3 "file:$DB?mode=ro" \
  "select 'INSERT INTO \"schema_version\" (version, applied_at) VALUES ('''||version||''', '''||applied_at||''');' from (select distinct version, applied_at from schema_version) order by version;"
