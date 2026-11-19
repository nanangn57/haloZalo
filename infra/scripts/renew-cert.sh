#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
[[ -f "$ENV_FILE" ]] || { echo "Missing infra/.env" >&2; exit 1; }
compose() { docker compose --project-directory "$DEPLOY_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
compose --profile tools run --rm certbot renew --webroot -w /var/www/certbot
compose exec -T nginx nginx -s reload
