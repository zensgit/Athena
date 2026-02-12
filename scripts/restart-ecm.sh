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
ENV_MAIL_FILE="${REPO_ROOT}/.env.mail"
ENV_MAIL_EXAMPLE_FILE="${REPO_ROOT}/.env.mail.example"
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

ensure_env_mail() {
  if [[ -f "${ENV_MAIL_FILE}" ]]; then
    return 0
  fi

  if [[ -f "${ENV_MAIL_EXAMPLE_FILE}" ]]; then
    cp "${ENV_MAIL_EXAMPLE_FILE}" "${ENV_MAIL_FILE}"
    chmod 600 "${ENV_MAIL_FILE}" 2>/dev/null || true
    echo "info: .env.mail was missing; created from .env.mail.example (placeholders only)."
    return 0
  fi

  cat > "${ENV_MAIL_FILE}" <<'EOF'
# Placeholder for mail automation OAuth/IMAP settings (gitignored).
# If you need real mail automation, copy from .env.mail.example and fill values.
EOF
  chmod 600 "${ENV_MAIL_FILE}" 2>/dev/null || true
  echo "info: .env.mail was missing; created a placeholder file."
}

ensure_env_mail

repair_rabbitmq_plugins_expand() {
  local rabbitmq_cid
  local rabbitmq_volume

  rabbitmq_cid="$(docker_compose ps -q rabbitmq 2>/dev/null || true)"
  rabbitmq_volume=""

  if [[ -n "${rabbitmq_cid}" ]]; then
    rabbitmq_volume="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/rabbitmq"}}{{.Name}}{{end}}{{end}}' "${rabbitmq_cid}" 2>/dev/null || true)"
    docker update --restart=no "${rabbitmq_cid}" >/dev/null 2>&1 || true
    docker stop "${rabbitmq_cid}" >/dev/null 2>&1 || true
  fi

  if [[ -z "${rabbitmq_volume}" ]]; then
    rabbitmq_volume="$(docker volume ls --format '{{.Name}}' | grep -E '(^|_)rabbitmq_data$' | head -n 1 || true)"
  fi

  if [[ -n "${rabbitmq_volume}" ]]; then
    docker run --rm -v "${rabbitmq_volume}:/data" alpine sh -lc '
      set -e
      found=0
      for d in /data/mnesia/*-plugins-expand; do
        [ -d "$d" ] || continue
        found=1
        ts=$(date +%s)
        mv "$d" "$d.stale.$ts"
        echo "RabbitMQ stale plugins dir moved: $d -> $d.stale.$ts"
      done
      if [ "$found" -eq 0 ]; then
        echo "RabbitMQ stale plugins dir not found."
      fi
    ' || true
  fi

  if [[ -n "${rabbitmq_cid}" ]]; then
    docker update --restart=unless-stopped "${rabbitmq_cid}" >/dev/null 2>&1 || true
    docker start "${rabbitmq_cid}" >/dev/null 2>&1 || true
  fi
}

repair_rabbitmq_plugins_expand

docker_compose build ecm-core ecm-frontend
docker_compose up -d --no-deps --force-recreate ecm-core ecm-frontend

echo "OK"
echo "Frontend: http://localhost:${ECM_FRONTEND_PORT:-3000}/"
echo "Backend:  http://localhost:${ECM_API_PORT:-8080}/"
