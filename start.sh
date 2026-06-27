#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

set -a; [ -f "$ROOT/.env" ] && source "$ROOT/.env"; set +a

# --- PostgreSQL (skipped when DB_URL points to an external database) ---
if [ -n "${DB_URL:-}" ]; then
    echo "DB_URL is set — using external database, skipping Docker."
else
    if [ "$(docker inspect -f '{{.State.Running}}' budget-postgres 2>/dev/null)" = "true" ]; then
        echo "budget-postgres is already running."
    else
        echo "Starting budget-postgres..."
        if docker inspect budget-postgres &>/dev/null; then
            docker start budget-postgres
        else
            docker run -d \
                --name budget-postgres \
                -e POSTGRES_DB=budget \
                -e POSTGRES_USER=budget \
                -e POSTGRES_PASSWORD=budget \
                -p 5432:5432 \
                postgres:16
        fi

        echo "Waiting for PostgreSQL to be ready..."
        until docker exec budget-postgres pg_isready -U budget -d budget &>/dev/null; do
            sleep 1
        done
        echo "PostgreSQL is ready."
    fi
fi

# --- Spring Boot ---
echo "Starting Spring Boot..."
cd "$ROOT/backend"
./mvnw spring-boot:run &

echo "Waiting for Spring Boot to be ready..."
until lsof -ti:8080 &>/dev/null; do
    sleep 1
done

SPRING_PID=$(lsof -ti:8080 | head -1)
echo "$SPRING_PID" > "$ROOT/.spring-boot.pid"
echo "Spring Boot started (PID $SPRING_PID)."

# --- Angular ---
echo "Starting Angular..."
cd "$ROOT/frontend"
npx ng serve -c development &
ANGULAR_PID=$!
echo "$ANGULAR_PID" > "$ROOT/.frontend.pid"

echo "Waiting for Angular to be ready..."
until lsof -ti:4200 &>/dev/null; do
    sleep 1
done
echo "Angular started (PID $ANGULAR_PID). Use stop.sh to shut everything down."
