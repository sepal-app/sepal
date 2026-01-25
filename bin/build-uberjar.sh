#!/usr/bin/env bash
#
# Build the Sepal uberjar locally.
#
# Usage: bin/build-uberjar.sh [--skip-frontend]
#
# Options:
#   --skip-frontend  Skip building frontend assets (use if already built)
#

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

SKIP_FRONTEND=false

for arg in "$@"; do
    case $arg in
        --skip-frontend)
            SKIP_FRONTEND=true
            shift
            ;;
    esac
done

# Build frontend assets
if [[ "$SKIP_FRONTEND" == "false" ]]; then
    echo "Building frontend assets..."
    cd bases/app
    npm install
    npm run build
    cd "$PROJECT_ROOT"
else
    echo "Skipping frontend build..."
fi

# Build uberjar
echo "Building uberjar..."
cd projects/app
clojure -T:build uber

echo ""
echo "Successfully built: projects/app/target/sepal.jar"
echo ""
echo "To run locally:"
echo "  java -Duser.timezone=UTC --enable-native-access=ALL-UNNAMED -jar projects/app/target/sepal.jar"
