#!/usr/bin/env bash
# Starts PostgreSQL and Redis for running messaging on this machine with `mvn spring-boot:run`.
#   ./local-deps.sh up       start both (data is kept in the halozalo-messaging-pg volume)
#   ./local-deps.sh down     stop and remove the containers, keep the data
#   ./local-deps.sh reset    stop, remove the containers and delete the data
#   ./local-deps.sh psql     open psql in the database
# Uses the same ports as docker-compose.yml, so run one or the other, not both.
set -euo pipefail

PG=halozalo-messaging-postgres
REDIS=halozalo-messaging-redis
VOLUME=halozalo-messaging-pg

# Same defaults as .env.example. Export them first to use other values.
PG_USER=${POSTGRES_USER:-messaging}
PG_PASSWORD=${POSTGRES_PASSWORD:-messaging}
PG_DB=${POSTGRES_DB:-messaging}

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
      -p 127.0.0.1:5432:5432 \
      -v "$VOLUME":/var/lib/postgresql/data \
      --health-cmd "pg_isready -U $PG_USER -d $PG_DB" --health-interval 2s --health-retries 30 \
      postgres:17-alpine >/dev/null
    echo "started $PG on localhost:5432 (database $PG_DB, user $PG_USER)"
  fi

  if running "$REDIS"; then
    echo "$REDIS already running"
  else
    docker rm -f "$REDIS" >/dev/null 2>&1 || true
    docker run -d --name "$REDIS" \
      -p 127.0.0.1:6379:6379 \
      --health-cmd "redis-cli ping" --health-interval 2s --health-retries 30 \
      redis:8-alpine >/dev/null
    echo "started $REDIS on localhost:6379"
  fi

  for name in "$PG" "$REDIS"; do
    for _ in $(seq 1 60); do
      [ "$(docker inspect -f '{{.State.Health.Status}}' "$name")" = healthy ] && break
      sleep 1
    done
    echo "$name: $(docker inspect -f '{{.State.Health.Status}}' "$name")"
  done
}

down() {
  docker rm -f "$PG" "$REDIS" >/dev/null 2>&1 || true
  echo "stopped $PG and $REDIS (data kept in volume $VOLUME)"
}

case "${1:-}" in
  up) up ;;
  down) down ;;
  reset) down; docker volume rm "$VOLUME" >/dev/null 2>&1 || true; echo "deleted volume $VOLUME" ;;
  psql) docker exec -it "$PG" psql -U "$PG_USER" -d "$PG_DB" ;;
  *) echo "usage: $0 up|down|reset|psql" >&2; exit 1 ;;
esac
