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
#
# `.schema` emits no data, so the lookup tables a migration seeds -- taxon_rank
# and friends -- would come out empty, and a freshly provisioned database would
# then fail every insert that references one. This script therefore dumps their
# rows itself.
#
# It finds them rather than listing them: a table with a single text primary key
# that something else has a foreign key onto is a lookup table, and a lookup
# table's rows are reference data that belongs in the baseline. Every user-data
# table has an integer primary key, so none of them match -- taxon has 452,000
# rows and is correctly skipped.
#
# That rule exists because the list went stale. It used to be a comment saying
# "append the taxon_rank seed by hand", written when taxon_rank was the only
# such table; material_status, material_change_reason and accession_received_type
# arrived later and were silently dropped from the output for anyone who
# followed it.
set -Eeuo pipefail
DB="$1"
RO="file:$DB?mode=ro"

# The schema itself.
sqlite3 "$RO" ".schema" \
  | sed -e 's/^CREATE TABLE IF NOT EXISTS /CREATE TABLE /' \
  | grep -v "^CREATE TABLE sqlite_sequence(name,seq);$" \
  | grep -vE "^CREATE TABLE '[a-z_]+_fts_(data|idx|docsize|config)'" \
  | perl -0pe "s{\n/\* [a-z_]+\([a-z_,]+\) \*/;}{;}g"

# The seeded lookup tables, discovered by shape.
SEED_TABLES=$(sqlite3 "$RO" "
  select m.name from sqlite_master m
  where m.type = 'table'
    and m.name not like 'sqlite_%'
    and (select count(*) from pragma_table_info(m.name) where pk > 0) = 1
    and (select lower(type) from pragma_table_info(m.name) where pk > 0) = 'text'
    and exists (select 1 from sqlite_master m2
                join pragma_foreign_key_list(m2.name) fk
                where m2.type = 'table' and fk.\"table\" = m.name)
  order by m.name;")

for table in $SEED_TABLES; do
  rows=$(sqlite3 "$RO" "select count(*) from \"$table\";")
  # A lookup table with no rows means the migration that seeds it did not run,
  # or seeds nothing. Either way the baseline should not silently claim it is
  # empty -- say so on stderr and carry on.
  if [ "$rows" -eq 0 ]; then
    echo "warning: lookup table $table is empty; nothing seeded into the baseline" >&2
    continue
  fi
  sqlite3 "$RO" ".mode insert \"$table\"" "select * from \"$table\";"
done

# Last, so the version rows sit at the end of the file the way they always have.
sqlite3 "$RO" \
  "select 'INSERT INTO \"schema_version\" (version, applied_at) VALUES ('''||version||''', '''||applied_at||''');' from (select version, min(applied_at) as applied_at from schema_version group by version) order by version;"
