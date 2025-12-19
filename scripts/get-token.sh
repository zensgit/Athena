#!/usr/bin/env bash
set -euo pipefail

# Fetch a Keycloak access token for local testing.
# Usage:
#   ./scripts/get-token.sh admin admin
#
# Output:
#   tmp/<username>.access_token

USERNAME="${1:-admin}"
PASSWORD="${2:-admin}"

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}"
REALM="${KEYCLOAK_REALM:-ecm}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-unified-portal}"

mkdir -p tmp

token_json=$(curl -fsS -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=${CLIENT_ID}&username=${USERNAME}&password=${PASSWORD}")

if command -v jq >/dev/null 2>&1; then
  echo "$token_json" | jq -r .access_token > "tmp/${USERNAME}.access_token"
else
  echo "$token_json" | sed -n 's/.*\"access_token\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' > "tmp/${USERNAME}.access_token"
fi

chmod 600 "tmp/${USERNAME}.access_token"
echo "Wrote tmp/${USERNAME}.access_token"
