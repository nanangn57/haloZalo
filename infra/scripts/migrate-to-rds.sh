#!/usr/bin/env bash
# One-time: dump Compose Postgres → Amazon RDS, then set DB_HOST in infra/.env.
# Run on the EC2 host that still has container halozalo-postgres-1 and can reach RDS.
#
#   DB_HOST_RDS=xxx.rds.amazonaws.com ./scripts/migrate-to-rds.sh
#   ALLOW_DIRTY=1 ./scripts/deploy-ec2.sh
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
: "${DB_HOST_RDS:?Set DB_HOST_RDS to the RDS endpoint hostname}"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_NAME:?}"
: "${DB_USER:?}"
: "${DB_PASSWORD:?}"

RDS_USER="${DB_USER}"
RDS_PASSWORD="${RDS_PASSWORD_FROM_SSM:-$DB_PASSWORD}"

DUMP=/tmp/halozalo-pg.dump
echo "==> pg_dump from Compose postgres"
docker exec -e PGPASSWORD="$DB_PASSWORD" halozalo-postgres-1 \
  pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc -f /tmp/halozalo-pg.dump
docker cp halozalo-postgres-1:/tmp/halozalo-pg.dump "$DUMP"

echo "==> restore into RDS ${DB_HOST_RDS}"
docker run --rm --network host \
  -e PGPASSWORD="$RDS_PASSWORD" \
  -v "$DUMP:/dump/halozalo-pg.dump:ro" \
  postgres:16 \
  pg_restore -h "$DB_HOST_RDS" -p 5432 -U "$RDS_USER" -d "$DB_NAME" \
    --no-owner --no-acl --clean --if-exists /dump/halozalo-pg.dump \
  || true

echo "==> update infra/.env DB_HOST=${DB_HOST_RDS}"
if grep -q '^DB_HOST=' "$ENV_FILE"; then
  sed -i.bak "s|^DB_HOST=.*|DB_HOST=${DB_HOST_RDS}|" "$ENV_FILE"
else
  echo "DB_HOST=${DB_HOST_RDS}" >> "$ENV_FILE"
fi
if [[ -n "${RDS_PASSWORD_FROM_SSM:-}" ]]; then
  sed -i.bak "s|^DB_PASSWORD=.*|DB_PASSWORD=${RDS_PASSWORD}|" "$ENV_FILE"
fi

echo "==> done. Next: ALLOW_DIRTY=1 ./scripts/deploy-ec2.sh"
echo "    Then stop local DB: docker compose --profile local-db stop postgres"
