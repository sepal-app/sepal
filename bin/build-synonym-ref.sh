#!/usr/bin/env bash
# Build the read-only WFO synonym reference file from a full WFO Plantlist
# database.
#
# One file per machine, never per garden: the 1M synonym rows are shared
# reference data, and loading them into every garden database would roughly
# triple it. See plans/032-taxon-synonymy.md.
#
#   usage: WFO_DATABASE_PATH=wfo_plantlist_2025-06.db bin/build-synonym-ref.sh
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

FORMAT_VERSION="1"

if [ -z "${WFO_DATABASE_PATH:-}" ]; then
    echo "error: WFO_DATABASE_PATH is not set" >&2
    exit 1
fi
if [ ! -f "$WFO_DATABASE_PATH" ]; then
    echo "error: no WFO database at $WFO_DATABASE_PATH" >&2
    exit 1
fi

WFO_VERSION=$(basename "$WFO_DATABASE_PATH" .db | sed 's/wfo_plantlist_//')
OUTPUT_PATH=${OUTPUT_PATH:-dist/sepal-synonyms-${WFO_VERSION}.db}
mkdir -p "$(dirname "$OUTPUT_PATH")"
rm -f "$OUTPUT_PATH" "${OUTPUT_PATH}-wal" "${OUTPUT_PATH}-shm"

echo "Building $OUTPUT_PATH from $WFO_DATABASE_PATH (WFO $WFO_VERSION)"

sqlite3 "$OUTPUT_PATH" <<SQL
attach '$WFO_DATABASE_PATH' as wfo;

-- accepted_core is the 14-character stable part of the WFO id,
-- 'wfo-0000283538' out of 'wfo-0000283538-2025-06'. The garden side joins on
-- substr(taxon.wfo_taxon_id, 1, 14). Measured 2026-09-02: matching the full
-- versioned id across a release gap resolves 0 of 1,019,425 rows; matching the
-- core resolves 1,014,088.
create table syn (
  name text not null,
  accepted_core text not null,
  accepted_wfo_id text not null,
  name_id text not null
) strict;

insert into syn
select n.scientificName, substr(s.taxonID, 1, 14), s.taxonID, s.nameID
  from wfo.synonym s join wfo.name n on n.ID = s.nameID;

create index syn_accepted_core_idx on syn(accepted_core);

-- External content: the name text is read from syn at query time rather than
-- copied. A standalone FTS5 build measured 57.4 MiB of content copy.
create virtual table syn_fts using fts5(name, content='syn',
                                        content_rowid='rowid',
                                        tokenize='unicode61');
insert into syn_fts(syn_fts) values('rebuild');

create table metadata (key text primary key, value text) strict;
insert into metadata (key, value) values
  ('wfo_plant_list.version', '$WFO_VERSION'),
  ('format_version', '$FORMAT_VERSION');

vacuum;
SQL

ROWS=$(sqlite3 "$OUTPUT_PATH" "select count(*) from syn;")
CONTENT=$(sqlite3 "$OUTPUT_PATH" \
    "select count(*) from sqlite_master where name = 'syn_fts_content';")
BYTES=$(wc -c < "$OUTPUT_PATH" | tr -d ' ')
SHA256=$(sha256sum "$OUTPUT_PATH" | awk '{print $1}')

# A silently partial build is the failure this guards. 1,019,425 is the row
# count of WFO 2025-06's synonym table, one row per nameID.
if [ "$ROWS" -lt 900000 ]; then
    echo "error: only $ROWS synonym rows; expected about 1,019,425" >&2
    exit 1
fi
# External content is the whole size argument. If FTS5 copied the text, the
# content table exists and the file is ~57 MiB larger than it should be.
if [ "$CONTENT" != "0" ]; then
    echo "error: syn_fts copied its content; external content did not take" >&2
    exit 1
fi
# 129.8 MiB (136,105,984 bytes) measured for 2025-06. The band is deliberately
# loose: the row count above is the real guard against a partial build, and the
# syn_fts_content check is the specific guard against the FTS shape regressing.
# This only needs to catch a gross change.
if [ "$BYTES" -lt 104857600 ] || [ "$BYTES" -gt 272629760 ]; then
    echo "error: $OUTPUT_PATH is $BYTES bytes, outside the expected range" >&2
    exit 1
fi

echo "  rows:    $ROWS"
echo "  bytes:   $BYTES"
echo "  sha256:  $SHA256"
