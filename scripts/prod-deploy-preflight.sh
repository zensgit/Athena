#!/usr/bin/env bash
# Production deploy preflight.
#
# Guards the B1/B2 production compose path before `docker compose up`:
# - requires Docker Compose v2 (`docker compose`), not legacy `docker-compose`
# - rejects temporary ddl-auto=update compose overrides
# - validates required prod env KEY NAMES without printing values
# - validates merged base+prod compose shape without requiring a daemon
#
# Usage:
#   scripts/prod-deploy-preflight.sh --env-file /etc/athena/prod.env --require-daemon
#   ATHENA_PROD_ENV=/etc/athena/prod.env scripts/prod-deploy-preflight.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROD_ENV="${ATHENA_PROD_ENV:-/etc/athena/prod.env}"
REQUIRE_DAEMON=0

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

warn() {
  echo "WARN: $1" >&2
}

info() {
  echo "OK: $1"
}

usage() {
  cat <<'EOF'
Usage: scripts/prod-deploy-preflight.sh [--env-file PATH] [--require-daemon]

Runs production deploy preflight checks without printing secret values.

Options:
  --env-file PATH     Prod env file to validate. Defaults to ATHENA_PROD_ENV or /etc/athena/prod.env.
  --require-daemon    Fail if Docker daemon is unreachable. Use this on the real deploy host.
  -h, --help          Show this help.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file)
      [ "$#" -ge 2 ] || fail "--env-file requires a path"
      PROD_ENV="$2"
      shift 2
      ;;
    --require-daemon)
      REQUIRE_DAEMON=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

tmp_files=()
cleanup() {
  if [ "${#tmp_files[@]}" -gt 0 ]; then
    rm -f "${tmp_files[@]}"
  fi
}
trap cleanup EXIT

require_file() {
  [ -f "$1" ] || fail "missing file: $1"
}

env_has_value() {
  local key="$1"
  grep -Eq "^[[:space:]]*(export[[:space:]]+)?${key}=[^[:space:]].*" "$PROD_ENV"
}

require_env_key() {
  local key="$1"
  env_has_value "$key" || fail "prod env is missing required key or has a blank value: ${key}"
}

file_mode() {
  if stat -f "%Lp" "$1" >/dev/null 2>&1; then
    stat -f "%Lp" "$1"
  elif stat -c "%a" "$1" >/dev/null 2>&1; then
    stat -c "%a" "$1"
  else
    echo "unknown"
  fi
}

echo "prod_deploy_preflight: start"

require_file docker-compose.yml
require_file docker-compose.prod.yml
require_file scripts/b1b2-prod-config-check.sh

if ! command -v docker >/dev/null 2>&1; then
  fail "docker CLI is required"
fi

if ! docker compose version >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    fail "legacy docker-compose is present, but prod config requires Docker Compose v2 ('docker compose') for !reset/!override"
  fi
  fail "Docker Compose v2 plugin is required ('docker compose version' failed)"
fi
info "Docker Compose v2 plugin is available"

if command -v docker-compose >/dev/null 2>&1; then
  warn "legacy docker-compose is installed; do NOT use it for production because it rejects !reset/!override"
fi

if docker info >/dev/null 2>&1; then
  info "Docker daemon is reachable"
else
  if [ "$REQUIRE_DAEMON" -eq 1 ]; then
    fail "Docker daemon is not reachable; production deploy cannot proceed"
  fi
  warn "Docker daemon is not reachable; continuing with static/config checks only"
fi

[ -f "$PROD_ENV" ] || fail "prod env file is missing: ${PROD_ENV}"
info "prod env file exists (values not printed)"

mode="$(file_mode "$PROD_ENV")"
case "$mode" in
  400|600) info "prod env file permissions look restricted (${mode})" ;;
  unknown) warn "could not determine prod env file permissions" ;;
  *) warn "prod env file permissions are ${mode}; prefer 600 or 400" ;;
esac

if [ -f .tmp.prod.env ] || [ "$(basename "$PROD_ENV")" = ".tmp.prod.env" ]; then
  fail ".tmp.prod.env is a temporary in-repo file and must not be used for production"
fi

if [ -f docker-compose.ddl-update.yml ]; then
  fail "remove docker-compose.ddl-update.yml; ddl-auto=update is an emergency workaround, not a deployment path"
fi

if grep -RE "SPRING_JPA_HIBERNATE_DDL_AUTO[=:][[:space:]]*update" docker-compose*.yml >/dev/null 2>&1; then
  fail "compose files must not set SPRING_JPA_HIBERNATE_DDL_AUTO=update for production"
fi
info "no production ddl-auto=update compose override detected"

if git ls-files .env ecm-frontend/.env | grep -q .; then
  fail ".env or ecm-frontend/.env is tracked; S1 must stay in force before production deploy"
fi
info ".env files remain untracked"

required_env_keys=(
  POSTGRES_DB
  POSTGRES_USER
  POSTGRES_PASSWORD
  ELASTIC_PASSWORD
  REDIS_PASSWORD
  RABBITMQ_USER
  RABBITMQ_PASSWORD
  MINIO_ROOT_USER
  MINIO_ROOT_PASSWORD
  GF_SECURITY_ADMIN_USER
  GF_SECURITY_ADMIN_PASSWORD
  KEYCLOAK_USER
  KEYCLOAK_PASSWORD
  KEYCLOAK_DB_USER
  KEYCLOAK_DB_PASSWORD
  ECM_KEYCLOAK_PUBLIC_HOST
  ECM_JWT_ISSUER_URI
  ECM_JWT_JWK_SET_URI
  ECM_SECURITY_CORS_ALLOWED_ORIGINS
)

for key in "${required_env_keys[@]}"; do
  require_env_key "$key"
done
info "required prod env key names are present"

if env_has_value JWT_SECRET; then
  fail "JWT_SECRET is a dead key after P0a-1; remove it from prod env instead of rotating it"
fi
info "JWT_SECRET is absent"

echo "prod_deploy_preflight: run B1/B2 static config guard"
scripts/b1b2-prod-config-check.sh >/dev/null
info "B1/B2 static guard passed"

merged_config="$(mktemp)"
merged_error="$(mktemp)"
tmp_files+=("$merged_config" "$merged_error")

if ! docker compose --env-file "$PROD_ENV" -f docker-compose.yml -f docker-compose.prod.yml config >"$merged_config" 2>"$merged_error"; then
  fail "docker compose config failed for base+prod with the supplied env file; fix missing/invalid keys before deploy (secret values suppressed)"
fi
info "base+prod compose config validates with supplied env file"

grep -Eq 'SPRING_PROFILES_ACTIVE:[[:space:]]*"?prod"?' "$merged_config" \
  || fail "merged prod config must run ecm-core with SPRING_PROFILES_ACTIVE=prod"

if grep -Eq 'SPRING_JPA_HIBERNATE_DDL_AUTO:[[:space:]]*"?update"?' "$merged_config"; then
  fail "merged prod config must not set SPRING_JPA_HIBERNATE_DDL_AUTO=update"
fi

python3 - "$merged_config" <<'PY'
import sys
import yaml

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = yaml.safe_load(fh)

services = data.get("services", {})
core = services.get("ecm-core", {})
if core.get("env_file"):
    raise SystemExit("ecm-core env_file must be empty in prod")

es = services.get("elasticsearch", {})
healthcheck = es.get("healthcheck", {})
test = healthcheck.get("test")
if isinstance(test, list):
    healthcheck_text = " ".join(str(part) for part in test)
else:
    healthcheck_text = str(test or "")
if "ELASTIC_PASSWORD" not in healthcheck_text or "-u" not in healthcheck_text:
    raise SystemExit("prod Elasticsearch healthcheck must authenticate with ELASTIC_PASSWORD")
PY
info "merged prod config keeps prod profile, no ddl-auto update, no ecm-core env_file, and authenticated Elasticsearch healthcheck"

echo "prod_deploy_preflight: ok"
echo "NOTE: This does not prove runtime cutover, TLS, Keycloak token issuer, backup/restore, or B4 smoke."
