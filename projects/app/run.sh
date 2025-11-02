#!/usr/bin/env bash

set -Eeuo pipefail

export DATABASE_URL="sqlite:$DATABASE_PATH"

bin/dbmate -e DATABASE_URL --no-dump-schema migrate &&
    cd projects/app &&
    clojure -M:main
