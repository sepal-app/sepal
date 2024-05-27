#!/usr/bin/env bash

set -Eeuxo pipefail

clojure -M:migrations --profile "${SEPAL_ENVIRONMENT}" migrate && \
cd projects/app && \
clojure -M:main
