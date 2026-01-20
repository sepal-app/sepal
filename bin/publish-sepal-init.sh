#!/usr/bin/env bash
#
# Build and publish a Sepal init database to GitHub Releases.
#
# Usage:
#   WFO_DATABASE_PATH=wfo_plantlist_2025-12_2.db bin/publish-sepal-init.sh
#
# Requirements:
#   - gh CLI (authenticated)
#   - jq
#   - sqlite3
#

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# WFO_DATABASE_PATH is required
if [[ -z "${WFO_DATABASE_PATH:-}" ]]; then
    echo "Error: WFO_DATABASE_PATH is required" >&2
    echo "" >&2
    echo "Usage: WFO_DATABASE_PATH=<path> $0" >&2
    exit 1
fi

# Check for required tools
for cmd in gh jq sqlite3; do
    if ! command -v "$cmd" &> /dev/null; then
        echo "Error: $cmd is required but not installed" >&2
        exit 1
    fi
done

# Check gh is authenticated
if ! gh auth status &> /dev/null; then
    echo "Error: gh CLI is not authenticated. Run 'gh auth login' first." >&2
    exit 1
fi

# Get the GitHub repo from git remote
REPO=$(gh repo view --json nameWithOwner -q '.nameWithOwner')
echo "Publishing to: $REPO"

# Get latest sepal-init release version and increment
echo "Checking latest release..."
LATEST_TAG=$(gh release list --limit 100 --json tagName -q '[.[].tagName | select(startswith("sepal-init-v"))] | first // "sepal-init-v0"')
LATEST_VERSION=$(echo "$LATEST_TAG" | grep -oE '[0-9]+$')
NEXT_VERSION=$((LATEST_VERSION + 1))
RELEASE_TAG="sepal-init-v${NEXT_VERSION}"

echo "  Latest: $LATEST_TAG"
echo "  Next: $RELEASE_TAG"
echo ""

# Build the init database
DB_FILE="dist/sepal-init-v${NEXT_VERSION}.db"
OUTPUT_PATH="$DB_FILE" "$SCRIPT_DIR/create-init-db.sh"

# Extract metadata from the database
SCHEMA_VERSION=$(sqlite3 "$DB_FILE" "SELECT value FROM metadata WHERE key = 'schema_version';")
WFO_VERSION=$(sqlite3 "$DB_FILE" "SELECT value FROM metadata WHERE key = 'wfo_plant_list.version';")
SIZE_MB=$(du -m "$DB_FILE" | cut -f1)

echo ""
echo "Building manifest..."

# Database URL for this release
DB_URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/sepal-init-v${NEXT_VERSION}.db"

# Create new version entry
NEW_ENTRY=$(jq -n \
    --argjson sv "$SCHEMA_VERSION" \
    --arg wv "$WFO_VERSION" \
    --argjson sz "$SIZE_MB" \
    --arg url "$DB_URL" \
    '{"schema_version": $sv, "wfo_plant_list.version": $wv, "size_mb": $sz, "url": $url}')

# Use temp file for manifest (never rely on local copy)
MANIFEST_FILE=$(mktemp --suffix=-sepal-init-manifest.json)
trap "rm -f $MANIFEST_FILE" EXIT

# Fetch existing manifest from latest release (if any)
TEMP_DOWNLOAD=$(mktemp -d)
if gh release download "$LATEST_TAG" --pattern "sepal-init-manifest.json" --dir "$TEMP_DOWNLOAD" 2>/dev/null; then
    echo "  Found existing manifest, prepending new version"
    # Prepend new entry to existing versions
    jq --argjson new "$NEW_ENTRY" '.versions = [$new] + .versions' "$TEMP_DOWNLOAD/sepal-init-manifest.json" > "$MANIFEST_FILE"
    rm -rf "$TEMP_DOWNLOAD"
else
    echo "  No existing manifest, creating new one"
    rm -rf "$TEMP_DOWNLOAD"
    # Create new manifest
    jq -n --argjson new "$NEW_ENTRY" '{versions: [$new]}' > "$MANIFEST_FILE"
fi

echo ""
jq . "$MANIFEST_FILE"

echo ""
echo "Creating GitHub release: $RELEASE_TAG"

# Copy manifest to properly named file for upload
MANIFEST_UPLOAD="dist/sepal-init-manifest.json"
cp "$MANIFEST_FILE" "$MANIFEST_UPLOAD"

# Create the release
gh release create "$RELEASE_TAG" \
    "$DB_FILE" \
    "$MANIFEST_UPLOAD" \
    --title "Sepal Init Database v${NEXT_VERSION}" \
    --notes "Sepal init database with WFO Plant List ${WFO_VERSION} data.

- Schema version: ${SCHEMA_VERSION}
- WFO Plant List version: ${WFO_VERSION}
- Size: ${SIZE_MB} MB"

echo ""
echo "Published successfully!"
echo "  Release: https://github.com/${REPO}/releases/tag/${RELEASE_TAG}"
echo "  Manifest URL: https://github.com/${REPO}/releases/latest/download/sepal-init-manifest.json"
