#!/usr/bin/env bash
set -euo pipefail

# Rebuild and restart the core services needed for local ECM testing.
# Usage:
#   ./scripts/restart-ecm.sh

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_HOME_DIR="${REPO_ROOT}/tmp/docker-home"
DOCKER_CONFIG_DIR="${DOCKER_HOME_DIR}/.docker"
mkdir -p "${DOCKER_CONFIG_DIR}"
if [[ ! -f "${DOCKER_CONFIG_DIR}/config.json" ]]; then
  echo "{}" > "${DOCKER_CONFIG_DIR}/config.json"
fi
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
export COMPOSE_BAKE=0

docker_compose() {
  if command -v docker-compose >/dev/null 2>&1; then
    (cd "${REPO_ROOT}" && HOME="${DOCKER_HOME_DIR}" DOCKER_CONFIG="${DOCKER_CONFIG_DIR}" docker-compose -f docker-compose.yml "$@")
  else
    HOME="${DOCKER_HOME_DIR}" DOCKER_CONFIG="${DOCKER_CONFIG_DIR}" docker compose --project-directory "${REPO_ROOT}" -f "${REPO_ROOT}/docker-compose.yml" "$@"
  fi
}

docker_compose build ecm-core ecm-frontend
docker_compose up -d --no-deps --force-recreate ecm-core ecm-frontend

echo "OK"
echo "Frontend: http://localhost:${ECM_FRONTEND_PORT:-3000}/"
echo "Backend:  http://localhost:${ECM_API_PORT:-8080}/"
