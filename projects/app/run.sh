#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# When running from Docker, bin/lib is at ./bin/lib relative to /app
# When running locally from projects/app, it's at ../../bin/lib
if [[ -f "$SCRIPT_DIR/bin/lib/env.sh" ]]; then
    source "$SCRIPT_DIR/bin/lib/env.sh"
else
    source "$SCRIPT_DIR/../../bin/lib/env.sh"
fi

SEPAL_DATA_HOME=$(get_sepal_data_home)

export SEPAL_DATA_HOME

# Ensure directory exists
mkdir -p "$SEPAL_DATA_HOME"

# Run the application
exec java \
    -Duser.timezone=UTC \
    --enable-native-access=ALL-UNNAMED \
    -jar sepal.jar
