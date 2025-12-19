#!/usr/bin/env bash
set -euo pipefail

# Create common local test users in Keycloak (ecm realm).
# Usage:
#   KEYCLOAK_URL=http://localhost:8180 KEYCLOAK_ADMIN=admin KEYCLOAK_ADMIN_PASSWORD=admin \
#     bash scripts/keycloak/create-test-users.sh
#
# Notes:
# - Requires: curl, jq
# - Does NOT print tokens.

REALM="${KEYCLOAK_REALM:-ecm}"
ADMIN_USER="${KEYCLOAK_ADMIN:-${KEYCLOAK_USER:-admin}}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-${KEYCLOAK_PASSWORD:-admin}}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}"

# Resolve repo root so docker compose works regardless of CWD.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

docker_compose() {
  docker compose --project-directory "${REPO_ROOT}" -f "${REPO_ROOT}/docker-compose.yml" "$@"
}

# NOTE:
# In some Docker setups Keycloak master realm is configured with "SSL required: external requests",
# which makes host->container HTTP token requests fail with "HTTPS required".
# When Keycloak runs in Docker Compose, prefer using kcadm.sh *inside* the container (localhost=internal).
use_kcadm=0
if command -v docker >/dev/null 2>&1; then
  if docker_compose ps -q keycloak >/dev/null 2>&1; then
    if [[ -n "$(docker_compose ps -q keycloak 2>/dev/null || true)" ]]; then
      use_kcadm=1
    fi
  fi
fi

kcadm() {
  docker_compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"
}

ensure_realm_role() {
  local role_name="$1"
  local role_description="$2"

  if [[ "${use_kcadm}" -eq 1 ]]; then
    if kcadm get "roles/${role_name}" -r "${REALM}" >/dev/null 2>&1; then
      return 0
    fi
    kcadm create roles -r "${REALM}" -s "name=${role_name}" -s "description=${role_description}" >/dev/null
    return 0
  fi

  local admin_token
  admin_token="$(
    curl -fsS -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "grant_type=password&client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASSWORD}" \
      | jq -r '.access_token'
  )"

  local code
  code="$(
    curl -sS -H "Authorization: Bearer ${admin_token}" \
      -o /dev/null -w '%{http_code}' \
      "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${role_name}" || true
  )"

  if [[ "${code}" == "200" ]]; then
    return 0
  fi

  curl -fsS -X POST -H "Authorization: Bearer ${admin_token}" -H "Content-Type: application/json" \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/roles" \
    -d "{\"name\":\"${role_name}\",\"description\":\"${role_description}\"}"
}

get_user_id() {
  local username="$1"
  if [[ "${use_kcadm}" -eq 1 ]]; then
    kcadm get users -r "${REALM}" -q "username=${username}" \
      | jq -r --arg username "${username}" '.[] | select(.username == $username) | .id' \
      | head -n 1
    return 0
  fi

  local admin_token
  admin_token="$(
    curl -fsS -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "grant_type=password&client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASSWORD}" \
      | jq -r '.access_token'
  )"

  curl -fsS -G -H "Authorization: Bearer ${admin_token}" -H "Content-Type: application/json" \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
    --data-urlencode "username=${username}" \
    --data-urlencode "exact=true" \
    | jq -r --arg username "${username}" '.[] | select(.username == $username) | .id' \
    | head -n 1
}

ensure_user() {
  local username="$1"
  local email="$2"
  local password="$3"
  local realm_role="$4"

  ensure_realm_role "${realm_role}" "Local test role: ${realm_role}"

  local user_id
  user_id="$(get_user_id "${username}")"

  if [[ -z "${user_id}" ]]; then
    if [[ "${use_kcadm}" -eq 1 ]]; then
      kcadm create users -r "${REALM}" \
        -s "username=${username}" \
        -s "enabled=true" \
        -s "email=${email}" \
        -s "emailVerified=true" >/dev/null
    else
      local admin_token
      admin_token="$(
        curl -fsS -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
          -H "Content-Type: application/x-www-form-urlencoded" \
          -d "grant_type=password&client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASSWORD}" \
          | jq -r '.access_token'
      )"
      curl -fsS -X POST -H "Authorization: Bearer ${admin_token}" -H "Content-Type: application/json" \
        "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
        -d "{\"username\":\"${username}\",\"enabled\":true,\"email\":\"${email}\",\"emailVerified\":true}"
    fi
    user_id="$(get_user_id "${username}")"
  fi

  if [[ -z "${user_id}" ]]; then
    echo "Failed to resolve user id for ${username}" >&2
    exit 1
  fi

  if [[ "${use_kcadm}" -eq 1 ]]; then
    kcadm set-password -r "${REALM}" --username "${username}" --new-password "${password}" >/dev/null

    # Idempotent role mapping (skip if already present)
    if ! kcadm get-roles -r "${REALM}" --uusername "${username}" \
      | jq -r '.[].name' \
      | grep -Fxq "${realm_role}"; then
      kcadm add-roles -r "${REALM}" --uusername "${username}" --rolename "${realm_role}" >/dev/null
    fi
  else
    local admin_token
    admin_token="$(
      curl -fsS -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password&client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASSWORD}" \
        | jq -r '.access_token'
    )"
    curl -fsS -X PUT -H "Authorization: Bearer ${admin_token}" -H "Content-Type: application/json" \
      "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${user_id}/reset-password" \
      -d "{\"type\":\"password\",\"temporary\":false,\"value\":\"${password}\"}"

    local role_json role_id role_name
    role_json="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${realm_role}")"
    role_id="$(echo "${role_json}" | jq -r '.id')"
    role_name="$(echo "${role_json}" | jq -r '.name')"

    curl -fsS -X POST -H "Authorization: Bearer ${admin_token}" -H "Content-Type: application/json" \
      "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${user_id}/role-mappings/realm" \
      -d "[{\"id\":\"${role_id}\",\"name\":\"${role_name}\"}]"
  fi

  echo "OK: ${username} (${realm_role})"
}

if [[ "${use_kcadm}" -eq 1 ]]; then
  # Authenticate once for the current container (avoids "HTTPS required" from host side).
  kcadm config credentials --server http://localhost:8080 --realm master --user "${ADMIN_USER}" --password "${ADMIN_PASSWORD}" >/dev/null
else
  # Proactive hint (common failure mode)
  master_token_status="$(
    curl -sS -o /dev/null -w "%{http_code}" -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "grant_type=password&client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASSWORD}" || true
  )"
  if [[ "${master_token_status}" == "403" ]]; then
    echo "Keycloak rejected HTTP token request (HTTPS required). Run this script with Keycloak in docker-compose, or adjust realm SSL requirements." >&2
    exit 1
  fi
fi

ensure_user "editor" "editor@example.com" "editor" "editor"
ensure_user "viewer" "viewer@example.com" "viewer" "viewer"
