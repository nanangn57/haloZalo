#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
# Prefer git root; fall back to parent of infra/ when ownership blocks git.
if ROOT_DIR="$(git -C "$DEPLOY_DIR" rev-parse --show-toplevel 2>/dev/null)"; then
  :
else
  ROOT_DIR="$(cd -- "$DEPLOY_DIR/.." && pwd)"
fi
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
ALLOW_DIRTY="${ALLOW_DIRTY:-0}"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$DEPLOY_DIR/.env.example" "$ENV_FILE"
  echo "Created infra/.env from example. Edit it, then run this script again." >&2
  exit 1
fi

if [[ "$ALLOW_DIRTY" != "1" ]]; then
  if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    && [[ -n "$(git -C "$ROOT_DIR" status --porcelain 2>/dev/null || true)" ]]; then
    echo "Git working tree is not clean. Commit/stash, or ALLOW_DIRTY=1 $0" >&2
    exit 1
  fi
fi

# Match @{upstream} when set. Deploy host must not keep hand edits to tracked
# files (nginx templates, compose, …) — those broke CD ff-merge repeatedly.
# Untracked infra/.env is preserved by reset --hard.
if git -C "$ROOT_DIR" rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
  upstream="$(git -C "$ROOT_DIR" rev-parse --abbrev-ref --symbolic-full-name '@{u}')"
  git -C "$ROOT_DIR" fetch --prune origin || true
  git -C "$ROOT_DIR" reset --hard "$upstream" || true
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
: "${DOMAIN:?Set DOMAIN in infra/.env}"
: "${ACME_EMAIL:?Set ACME_EMAIL in infra/.env}"
: "${DB_USER:?Set DB_USER in infra/.env}"
: "${DB_PASSWORD:?Set DB_PASSWORD in infra/.env}"

# Default Vite URLs from DOMAIN when unset.
export VITE_API_IDENTITY_BASE_URL="${VITE_API_IDENTITY_BASE_URL:-https://${DOMAIN}/api/identity}"
export VITE_API_MESSAGE_BASE_URL="${VITE_API_MESSAGE_BASE_URL:-https://${DOMAIN}/api/messaging}"
export VITE_WS_BASE_URL="${VITE_WS_BASE_URL:-wss://${DOMAIN}/api/messaging}"
export MESSAGING_MEDIA_BACKEND="${MESSAGING_MEDIA_BACKEND:-s3}"

compose() {
  docker compose --project-directory "$DEPLOY_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

mkdir -p "$DEPLOY_DIR/nginx/current" "$DEPLOY_DIR/certbot/conf" "$DEPLOY_DIR/certbot/www"
CERT_FILE="$DEPLOY_DIR/certbot/conf/live/$DOMAIN/fullchain.pem"

if [[ -s "$CERT_FILE" ]]; then
  sed "s|__DOMAIN__|$DOMAIN|g" "$DEPLOY_DIR/nginx/templates/https.conf.template" > "$DEPLOY_DIR/nginx/current/default.conf"
else
  sed "s|__DOMAIN__|$DOMAIN|g" "$DEPLOY_DIR/nginx/templates/http.conf.template" > "$DEPLOY_DIR/nginx/current/default.conf"
fi

compose config --quiet

# Local Docker Postgres when DB_HOST is empty/postgres; otherwise Amazon RDS.
DB_HOST_VALUE="${DB_HOST:-postgres}"
if [[ "$DB_HOST_VALUE" == "postgres" || "$DB_HOST_VALUE" == "localhost" || "$DB_HOST_VALUE" == "127.0.0.1" ]]; then
  compose --profile local-db up -d --build postgres app nginx
else
  echo "Using external DB_HOST=${DB_HOST_VALUE} (Amazon RDS) — skipping local postgres"
  compose up -d --build app nginx
  compose --profile local-db stop postgres 2>/dev/null || true
fi

# App recreate changes Docker DNS IP; refresh nginx so it does not keep a stale upstream.
compose up -d --force-recreate --no-deps nginx

if [[ ! -s "$CERT_FILE" ]]; then
  echo "Requesting Let's Encrypt certificate for $DOMAIN..."
  compose --profile tools run --rm certbot certonly \
    --webroot -w /var/www/certbot \
    --email "$ACME_EMAIL" -d "$DOMAIN" \
    --agree-tos --no-eff-email --non-interactive

  sed "s|__DOMAIN__|$DOMAIN|g" "$DEPLOY_DIR/nginx/templates/https.conf.template" > "$DEPLOY_DIR/nginx/current/default.conf"
  compose up -d --force-recreate nginx
else
  compose --profile tools run --rm certbot renew --webroot -w /var/www/certbot --quiet || true
  compose exec -T nginx nginx -s reload || compose up -d --force-recreate nginx
fi

compose ps
echo "Deployment finished."
echo "  Health: https://$DOMAIN/actuator/health"
echo "  App:    https://$DOMAIN/"
