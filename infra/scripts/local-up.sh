#!/usr/bin/env bash
# Stop module-local Postgres stacks and start one shared DB + identity + messaging.
#   ./scripts/local-up.sh          up (build)
#   ./scripts/local-up.sh down     stop local stack
#   ./scripts/local-up.sh reset    wipe volume and recreate
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  cp .env.local.example .env
  echo "created infra/.env from .env.local.example"
fi

stop_module_stacks() {
  docker rm -f halozalo-messaging-postgres-1 halozalo-identity-postgres-1 \
    halozalo-messaging-messaging-1 halozalo-identity-identity-1 \
    halozalo-messaging-postgres 2>/dev/null || true
  # Module compose projects (best-effort).
  (cd ../services/messaging && docker compose down 2>/dev/null) || true
  (cd ../services/identity && docker compose down 2>/dev/null) || true
}

case "${1:-up}" in
  up)
    stop_module_stacks
    docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
    echo "local stack: Postgres :5432, monolith app :8080 (identity+messaging+analytics)"
    ;;
  down)
    docker compose -f docker-compose.yml -f docker-compose.local.yml down
    ;;
  reset)
    docker compose -f docker-compose.yml -f docker-compose.local.yml down -v
    stop_module_stacks
    docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
    echo "local stack reset with empty volume"
    ;;
  *)
    echo "usage: $0 up|down|reset" >&2
    exit 1
    ;;
esac
