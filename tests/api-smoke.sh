#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

request() {
  local label="$1"
  local path="$2"
  local file="$3"
  printf '\n== %s ==\n' "$label"
  curl --fail-with-body -sS -X POST "$BASE_URL$path" \
    -H 'Content-Type: application/json' \
    --data-binary "@$file"
  printf '\n'
}

request "register" "/api/tenants/register" "$ROOT_DIR/tests/requests/registration.json"
request "evaluate" "/api/entitlements/evaluate" "$ROOT_DIR/tests/requests/evaluate.json"
request "consume" "/api/entitlements/consume" "$ROOT_DIR/tests/requests/consume.json"
request "command" "/api/commands" "$ROOT_DIR/tests/requests/command.json"

printf '\n== tenant state ==\n'
curl --fail-with-body -sS "$BASE_URL/api/tenants/acme"
printf '\n\nSmoke scenario completed successfully.\n'
