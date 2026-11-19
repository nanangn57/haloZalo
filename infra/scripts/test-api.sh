#!/usr/bin/env bash
set -uo pipefail
DOMAIN="${1:-halozalo.nltuanthanh.io.vn}"
BASE="https://$DOMAIN"

check() {
  local path="$1" expected="$2" code
  code="$(curl -sS -o /tmp/halozalo-check-body -w '%{http_code}' "$BASE$path" || true)"
  printf '%-44s HTTP %s (expected %s)\n' "$path" "${code:-000}" "$expected"
  [[ "$code" == "$expected" ]]
}

failed=0
check / 200 || failed=1
# /me is currently expected to return 401 without a session; change if auth behavior changes.
check /api/identity/me 401 || failed=1
# Messaging health may return 200 if actuator is exposed and the proxy route matches.
check /api/messaging/actuator/health 200 || failed=1
exit "$failed"
