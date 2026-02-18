#!/usr/bin/env bash
set -euo pipefail

# Rebuild the prebuilt frontend assets and refresh the running container.
#
# Why this exists:
# - docker-compose.override.yml uses Dockerfile.prebuilt for ecm-frontend.
# - Dockerfile.prebuilt only copies ecm-frontend/build into Nginx, it does NOT run "npm run build".
# - If source changes but build/ is stale, the running UI won't match the repo and E2E can fail.
#
# Usage:
#   ./scripts/rebuild-frontend-prebuilt.sh
#
# Optional env:
#   FRONTEND_REBUILD_WITH_DEPS=1  # also rebuild/start dependent services

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

FRONTEND_DIR="${REPO_ROOT}/ecm-frontend"
if [[ ! -d "${FRONTEND_DIR}" ]]; then
  echo "ERROR: frontend directory not found: ${FRONTEND_DIR}" >&2
  exit 1
fi

docker_compose() {
  if command -v docker-compose >/dev/null 2>&1; then
    (cd "${REPO_ROOT}" && docker-compose "$@")
  else
    docker compose "$@"
  fi
}

FRONTEND_REBUILD_WITH_DEPS="${FRONTEND_REBUILD_WITH_DEPS:-0}"

echo "== Building frontend static assets (ecm-frontend/build) =="
cd "${FRONTEND_DIR}"
if [[ ! -d node_modules ]]; then
  if [[ -f package-lock.json ]]; then
    npm ci --legacy-peer-deps --no-audit --no-fund
  else
    npm install --legacy-peer-deps --no-audit --no-fund
  fi
fi
npm run build

cd "${REPO_ROOT}"
echo "== Rebuilding/restarting ecm-frontend container =="
if [[ "${FRONTEND_REBUILD_WITH_DEPS}" == "1" ]]; then
  docker_compose up -d --build ecm-frontend
else
  docker_compose up -d --no-deps --build ecm-frontend
fi

echo "OK"
echo "Frontend: http://localhost:${ECM_FRONTEND_PORT:-3000}/"
