#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT/.spring-boot.pid"

# --- Spring Boot ---
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Stopping Spring Boot (PID $PID)..."
        kill "$PID"
        rm -f "$PID_FILE"
        echo "Spring Boot stopped."
    else
        echo "Spring Boot process $PID is not running. Cleaning up PID file."
        rm -f "$PID_FILE"
    fi
else
    echo "No Spring Boot PID file found. Nothing to stop."
fi

# --- PostgreSQL ---
if [ "$(docker inspect -f '{{.State.Running}}' budget-postgres 2>/dev/null)" = "true" ]; then
    echo "Stopping budget-postgres..."
    docker stop budget-postgres
    echo "budget-postgres stopped."
else
    echo "budget-postgres is not running."
fi
