#!/usr/bin/env bash
# Starts the PostgreSQL that messaging needs, set up like infra/docker-compose.yml: database halozalo,
# user halozalo. Flyway creates schema messaging and its tables when messaging starts.
#   ./local-deps.sh up       start it (data is kept in the halozalo-messaging-pg volume)
#   ./local-deps.sh down     stop and remove the container, keep the data
#   ./local-deps.sh reset    stop, remove the container and delete the data
#   ./local-deps.sh psql     open psql on schema messaging
# infra/docker-compose.yml uses the same port. Run one of them, not both; either works for messaging.
set -euo pipefail

PG=halozalo-messaging-postgres
VOLUME=halozalo-messaging-pg

# Same defaults as .env.example. Export them first to use other values.
PG_USER=${POSTGRES_USER:-halozalo}
PG_PASSWORD=${POSTGRES_PASSWORD:-halozalo}
PG_DB=halozalo

running() {
  docker ps --format '{{.Names}}' | grep -qx "$1"
}

up() {
  if running "$PG"; then
    echo "$PG already running"
  else
    docker rm -f "$PG" >/dev/null 2>&1 || true
    docker run -d --name "$PG" \
      -e POSTGRES_USER="$PG_USER" \
      -e POSTGRES_PASSWORD="$PG_PASSWORD" \
      -e POSTGRES_DB="$PG_DB" \
      -e TZ=UTC \
      -p 127.0.0.1:5432:5432 \
      -v "$VOLUME":/var/lib/postgresql/data \
      --health-cmd "pg_isready -U $PG_USER -d $PG_DB" --health-interval 2s --health-retries 30 \
      postgres:16 >/dev/null
    echo "started $PG on localhost:5432 (database $PG_DB, user $PG_USER)"
  fi
  for _ in $(seq 1 60); do
    [ "$(docker inspect -f '{{.State.Health.Status}}' "$PG")" = healthy ] && break
    sleep 1
  done
  echo "$PG: $(docker inspect -f '{{.State.Health.Status}}' "$PG")"
}

down() {
  docker rm -f "$PG" >/dev/null 2>&1 || true
  echo "stopped $PG (data kept in volume $VOLUME)"
}

case "${1:-}" in
  up) up ;;
  down) down ;;
  reset) down; docker volume rm "$VOLUME" >/dev/null 2>&1 || true; echo "deleted volume $VOLUME" ;;
  psql) docker exec -it "$PG" psql -U "$PG_USER" -d "$PG_DB" -c "SET search_path TO messaging" -c "\\dt" ;;
  *) echo "usage: $0 up|down|reset|psql" >&2; exit 1 ;;
esac
