#!/usr/bin/env bash
set -euo pipefail

# Rebuild and restart the core services needed for local ECM testing.
# Usage:
#   ./scripts/restart-ecm.sh [--mode fast|full]
#   ./scripts/restart-ecm.sh --fast
#   ./scripts/restart-ecm.sh --full

usage() {
  cat <<'EOF'
Usage: ./scripts/restart-ecm.sh [--mode fast|full]

Modes:
  fast  Build ecm-core with SKIP_LIBREOFFICE=true and run with JODCONVERTER_LOCAL_ENABLED=false.
  full  Keep LibreOffice/JODConverter local conversion enabled.

Examples:
  ./scripts/restart-ecm.sh --mode fast
  ./scripts/restart-ecm.sh --full
EOF
}

MODE="${ECM_RUN_MODE:-full}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      shift
      if [[ $# -eq 0 ]]; then
        echo "error: --mode requires a value (fast|full)" >&2
        usage
        exit 1
      fi
      MODE="$1"
      ;;
    --mode=*)
      MODE="${1#*=}"
      ;;
    --fast)
      MODE="fast"
      ;;
    --full)
      MODE="full"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

if [[ "${MODE}" != "fast" && "${MODE}" != "full" ]]; then
  echo "error: unsupported mode '${MODE}' (expected fast|full)" >&2
  usage
  exit 1
fi

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

CORE_BUILD_ARGS=()
JODCONVERTER_LOCAL_ENABLED_VALUE="true"
if [[ "${MODE}" == "fast" ]]; then
  CORE_BUILD_ARGS=(--build-arg SKIP_LIBREOFFICE=true)
  JODCONVERTER_LOCAL_ENABLED_VALUE="false"
fi

echo "mode: ${MODE}"
echo "JODCONVERTER_LOCAL_ENABLED=${JODCONVERTER_LOCAL_ENABLED_VALUE}"

# Keep ml-service/ecm-core/ecm-frontend in sync with repo changes (e.g., OCR deps/endpoints).
# Note: do not use --no-deps here; when the stack is stopped we want dependencies (DB/ES/Keycloak/etc.)
# to come up automatically via docker-compose.
docker_compose build ml-service ecm-frontend
docker_compose build "${CORE_BUILD_ARGS[@]}" ecm-core
JODCONVERTER_LOCAL_ENABLED="${JODCONVERTER_LOCAL_ENABLED_VALUE}" \
  docker_compose up -d --force-recreate ml-service ecm-core ecm-frontend

echo "OK"
echo "Frontend: http://localhost:${ECM_FRONTEND_PORT:-3000}/"
echo "Backend:  http://localhost:${ECM_API_PORT:-8080}/"
