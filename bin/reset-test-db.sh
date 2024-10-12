#!/usr/bin/env sh

DATABASE_URL=$TEST_DATABASE_URL \
    DB_NAME=sepal_test \
    DB_USER=sepal_test \
    SKIP_WFO_PLANTLIST=true \
    bin/reset-db.sh
