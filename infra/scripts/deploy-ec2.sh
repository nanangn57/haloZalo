#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(git -C "$DEPLOY_DIR" rev-parse --show-toplevel)"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$DEPLOY_DIR/.env.example" "$ENV_FILE"
  echo "Created infra/.env from example. Edit it, then run this script again." >&2
  exit 1
fi

# Refuse to overwrite developer/server changes accidentally.
if [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]; then
  echo "Git working tree is not clean. Commit/stash tracked changes before deployment." >&2
  echo "Ensure infra/.env and generated certificate/config files are ignored by Git." >&2
  exit 1
fi

git -C "$ROOT_DIR" pull --ff-only

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
: "${DOMAIN:?Set DOMAIN in infra/.env}"
: "${ACME_EMAIL:?Set ACME_EMAIL in infra/.env}"

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
compose up -d --build postgres identity messaging nginx

if [[ ! -s "$CERT_FILE" ]]; then
  echo "Requesting Let's Encrypt certificate for $DOMAIN..."
  compose --profile tools run --rm certbot certonly \
    --webroot -w /var/www/certbot \
    --email "$ACME_EMAIL" -d "$DOMAIN" \
    --agree-tos --no-eff-email --non-interactive

  sed "s|__DOMAIN__|$DOMAIN|g" "$DEPLOY_DIR/nginx/templates/https.conf.template" > "$DEPLOY_DIR/nginx/current/default.conf"
  compose up -d --force-recreate nginx
else
  # Renew only if due. A daily scheduled invocation is recommended for unattended renewal.
  compose --profile tools run --rm certbot renew --webroot -w /var/www/certbot --quiet || true
  compose exec -T nginx nginx -s reload || compose up -d --force-recreate nginx
fi

compose ps
echo "Deployment finished. Test: https://$DOMAIN/"
