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

usage() {
    cat <<'EOF'
Build and publish a Sepal init database and its synonym reference to GitHub
Releases.

Usage:
  WFO_DATABASE_PATH=<path> bin/publish-sepal-init.sh
  bin/publish-sepal-init.sh --help

This publishes a real, public release. There is no dry-run and no undo short of
deleting the release by hand afterwards.

What it does, in order:
  1. Picks the next release tag (see "Version numbering" below).
  2. Builds dist/sepal-init-vN.db          (bin/create-init-db.sh, ~35 MB)
  3. Builds dist/sepal-synonyms-<wfo>.db   (bin/build-synonym-ref.sh, ~127 MB)
     Both come from the same WFO Plant List, and the reference is named from
     the version read out of the init database, so the pair cannot drift.
  4. Downloads the previous release's sepal-init-manifest.json and prepends a
     new entry. Newest-first is load-bearing: the setup wizard's reader filters
     by schema_version and takes the first match.
  5. Creates the release with both databases and the manifest attached.

Version numbering:
  The tag is sepal-init-v<N+1>, where N comes from the FIRST sepal-init-v* tag
  `gh release list --limit 100` returns. gh orders releases newest-first by
  date, so N is the most recently created one -- not the highest-numbered.
  With no sepal-init release at all it falls back to v0, so the first run
  publishes v1.

  Two ways that goes wrong, neither guarded:
    - More than 100 releases of any kind in the repo would push the
      sepal-init tags off the list. It would then fall back to v0 and try to
      create a tag that already exists.
    - A sepal-init release created out of numeric order makes "most recent"
      and "highest" disagree, and the next tag collides.
  Both surface as a `gh release create` failure rather than silent damage.
  There is no way to force a version: edit the script or rename the tag
  afterwards.

Each new manifest entry carries:
  schema_version, wfo_plant_list.version, size_mb, sha256, url,
  synonyms {url, sha256, size_mb}

Older entries have no `synonyms` key. That is expected -- the reader treats an
absent key as "no reference available" and a garden then runs with local-only
synonym search. Until a release is published by this script, every install
behaves that way.

Environment:
  WFO_DATABASE_PATH   Required, and the only one worth setting. The full WFO
                      Plant List SQLite database the release is built from,
                      e.g. wfo_plantlist_2025-12_2.db. Both the init database
                      and the synonym reference are derived from it, and the
                      version string in the manifest and in the reference
                      file's name is parsed from this filename -- the part
                      after "wfo_plantlist_", minus ".db". Rename the file and
                      you rename the release's recorded WFO version.

  Deliberately not overridable from here, in case you go looking:

  OUTPUT_PATH         Honoured by create-init-db.sh and build-synonym-ref.sh
                      when run directly, but this script sets it per child so
                      the filenames match the release tag. Setting it in the
                      environment has no effect.
  SCHEMA_VERSION      A plain assignment in create-init-db.sh, not a default.
  FORMAT_VERSION      The same, in build-synonym-ref.sh.
  GH_REPO             Ignored: the target comes from `gh repo view`, which
                      prefers this checkout's git remote. Verified 2026-09-05 --
                      setting GH_REPO does not redirect the release. To publish
                      somewhere else, run from a checkout of that repo.

  To build either artefact without publishing anything, run its script
  directly and set OUTPUT_PATH there:

    WFO_DATABASE_PATH=<path> OUTPUT_PATH=/tmp/x.db bin/create-init-db.sh
    WFO_DATABASE_PATH=<path> OUTPUT_PATH=/tmp/y.db bin/build-synonym-ref.sh

Requirements:
  gh (authenticated), jq, sqlite3, and GNU coreutils -- sha256sum, du and
  mktemp --suffix are used, so run this inside the devenv shell rather than
  against the macOS base system.

Output:
  dist/ is gitignored. The build artefacts stay there after publishing; only
  the release assets are uploaded.
EOF
}

case "${1:-}" in
    -h|--help|help)
        usage
        exit 0
        ;;
    "")
        ;;
    *)
        echo "Error: unknown argument '$1'" >&2
        echo "" >&2
        usage >&2
        exit 1
        ;;
esac

# WFO_DATABASE_PATH is required
if [[ -z "${WFO_DATABASE_PATH:-}" ]]; then
    echo "Error: WFO_DATABASE_PATH is required" >&2
    echo "" >&2
    echo "Usage: WFO_DATABASE_PATH=<path> $0" >&2
    echo "Run '$0 --help' for what this publishes." >&2
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
SHA256=$(sha256sum "$DB_FILE" | awk '{print $1}')

# Build the synonym reference from the same WFO Plant List, named from the
# version just read out of the init database so both files are pinned to one
# WFO release by construction.
SYN_FILE="dist/sepal-synonyms-${WFO_VERSION}.db"
OUTPUT_PATH="$SYN_FILE" "$SCRIPT_DIR/build-synonym-ref.sh"
SYN_URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/$(basename "$SYN_FILE")"
SYN_SIZE_MB=$(du -m "$SYN_FILE" | cut -f1)
SYN_SHA256=$(sha256sum "$SYN_FILE" | awk '{print $1}')

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
    --arg sha "$SHA256" \
    --arg synurl "$SYN_URL" \
    --arg synsha "$SYN_SHA256" \
    --argjson synsz "$SYN_SIZE_MB" \
    '{"schema_version": $sv, "wfo_plant_list.version": $wv, "size_mb": $sz,
      "sha256": $sha, "url": $url,
      "synonyms": {"url": $synurl, "sha256": $synsha, "size_mb": $synsz}}')

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
    "$SYN_FILE" \
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
