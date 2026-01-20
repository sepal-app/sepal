#!/usr/bin/env bash
#
# Create a minimal init database for distribution.
# This database contains WFO Plant List taxon data in a compact format.
#
# Usage:
#   WFO_DATABASE_PATH=wfo_plantlist_2025-06.db bin/create-init-db.sh
#   WFO_DATABASE_PATH=wfo_plantlist_2025-06.db OUTPUT_PATH=my-init.db bin/create-init-db.sh
#

set -Eeuo pipefail

# Schema version for the init database format.
# Bump this when the init database schema changes (e.g., new columns in taxon table).
# Sepal declares which schema versions it can import, allowing old versions to
# find compatible init databases even after schema changes.
SCHEMA_VERSION="1"

# WFO_DATABASE_PATH is required
if [[ -z "${WFO_DATABASE_PATH:-}" ]]; then
    echo "Error: WFO_DATABASE_PATH is required" >&2
    echo "" >&2
    echo "Usage: WFO_DATABASE_PATH=<path> $0" >&2
    echo "Example: WFO_DATABASE_PATH=wfo_plantlist_2025-06.db $0" >&2
    exit 1
fi

# Extract WFO Plant List version from database filename (e.g., "2025-06" from "wfo_plantlist_2025-06.db")
WFO_VERSION=$(basename "$WFO_DATABASE_PATH" .db | sed 's/wfo_plantlist_//')

# Default output path (publish script can override with versioned name)
OUTPUT_PATH=${OUTPUT_PATH:-dist/sepal-init.db}

# Ensure output directory exists
mkdir -p "$(dirname "$OUTPUT_PATH")"

# Check that WFO database exists
if [[ ! -f "$WFO_DATABASE_PATH" ]]; then
    echo "Error: WFO database not found at $WFO_DATABASE_PATH" >&2
    exit 1
fi

echo "Creating init database..."
echo "  WFO Plant List source: $WFO_DATABASE_PATH (version: $WFO_VERSION)"
echo "  Output: $OUTPUT_PATH"
echo ""

# Remove existing output file and WAL/SHM files
rm -f "$OUTPUT_PATH" "${OUTPUT_PATH}-wal" "${OUTPUT_PATH}-shm"

# Create minimal schema for distribution
sqlite3 "$OUTPUT_PATH" <<'EOSQL'
CREATE TABLE taxon (
  id INTEGER PRIMARY KEY,
  wfo_taxon_id TEXT NOT NULL,
  name TEXT NOT NULL,
  author TEXT,
  rank TEXT NOT NULL,
  parent_id INTEGER
) STRICT;

CREATE TABLE metadata (
  key TEXT PRIMARY KEY,
  value TEXT
) STRICT;
EOSQL

# Populate taxon data from WFO database
sqlite3 -cmd "attach database \"${WFO_DATABASE_PATH}\" as wfo;" "$OUTPUT_PATH" <<'EOSQL'
-- Insert all taxa from WFO
INSERT INTO taxon (wfo_taxon_id, name, author, rank)
SELECT
  wfo_t.ID,
  wfo_n.scientificName,
  wfo_n.authorship,
  wfo_n.rank
FROM wfo.taxon wfo_t
JOIN wfo.name wfo_n ON wfo_n.ID = wfo_t.nameID;

-- Resolve parent_id by mapping WFO parentID to taxon id
UPDATE taxon
SET parent_id = (
  SELECT parent.id
  FROM taxon parent
  JOIN wfo.taxon wfo_t ON wfo_t.ID = taxon.wfo_taxon_id
  WHERE parent.wfo_taxon_id = wfo_t.parentID
)
WHERE wfo_taxon_id IS NOT NULL;
EOSQL

# Record metadata
sqlite3 "$OUTPUT_PATH" <<EOSQL
INSERT INTO metadata (key, value) VALUES ('schema_version', '${SCHEMA_VERSION}');
INSERT INTO metadata (key, value) VALUES ('wfo_plant_list.version', '${WFO_VERSION}');
EOSQL

# Vacuum to optimize file size
sqlite3 "$OUTPUT_PATH" "VACUUM;"

# Print summary
TAXON_COUNT=$(sqlite3 "$OUTPUT_PATH" "SELECT COUNT(*) FROM taxon;")
FILE_SIZE=$(ls -lh "$OUTPUT_PATH" | awk '{print $5}')
SHA256=$(sha256sum "$OUTPUT_PATH" | awk '{print $1}')

echo ""
echo "Init database created successfully!"
echo "  File: $OUTPUT_PATH"
echo "  Size: $FILE_SIZE"
echo "  Taxa: $TAXON_COUNT"
echo "  WFO Plant List version: $WFO_VERSION"
echo "  Schema version: $SCHEMA_VERSION"
echo "  SHA256: $SHA256"
echo ""
echo "Update manifest and upload to GitHub Releases"
