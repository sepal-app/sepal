#!/usr/bin/env bash
# Shared environment helpers for Sepal scripts

# Get Sepal data home directory.
# Priority: SEPAL_DATA_HOME > XDG_DATA_HOME/Sepal > platform default
get_sepal_data_home() {
    if [[ -n "${SEPAL_DATA_HOME:-}" ]]; then
        echo "$SEPAL_DATA_HOME"
    elif [[ -n "${XDG_DATA_HOME:-}" ]]; then
        echo "$XDG_DATA_HOME/Sepal"
    elif [[ "$(uname)" == "Darwin" ]]; then
        echo "$HOME/Library/Application Support/Sepal"
    else
        echo "$HOME/.local/share/Sepal"
    fi
}
