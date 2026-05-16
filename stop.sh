#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

set -a; [ -f "$ROOT/.env" ] && source "$ROOT/.env"; set +a
PID_FILE="$ROOT/.spring-boot.pid"
FRONTEND_PID_FILE="$ROOT/.frontend.pid"

# --- Angular ---
if [ -f "$FRONTEND_PID_FILE" ]; then
    PID=$(cat "$FRONTEND_PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Stopping Angular (PID $PID)..."
        kill "$PID"
        rm -f "$FRONTEND_PID_FILE"
        echo "Angular stopped."
    else
        echo "Angular process $PID is not running. Cleaning up PID file."
        rm -f "$FRONTEND_PID_FILE"
    fi
else
    echo "No Angular PID file found. Nothing to stop."
fi

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

# --- PostgreSQL (skipped when DB_URL points to an external database) ---
if [ -n "${DB_URL:-}" ]; then
    echo "DB_URL is set — using external database, skipping Docker stop."
elif [ "$(docker inspect -f '{{.State.Running}}' budget-postgres 2>/dev/null)" = "true" ]; then
    echo "Stopping budget-postgres..."
    docker stop budget-postgres
    echo "budget-postgres stopped."
else
    echo "budget-postgres is not running."
fi
