#!/usr/bin/env bash
# ===========================================
# Athena ECM — New Machine Bootstrap
# ===========================================
# Usage: ./scripts/bootstrap.sh [--skip-build] [--core-only]
#
# --skip-build   Pull images only, skip building ecm-core/ecm-frontend/ml-service
# --core-only    Start only essential services (postgres, redis, elasticsearch, keycloak, ecm-core, ecm-frontend)

set -euo pipefail
cd "$(dirname "$0")/.."

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

SKIP_BUILD=false
CORE_ONLY=false

for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
    --core-only)  CORE_ONLY=true ;;
    *) warn "Unknown argument: $arg" ;;
  esac
done

# -------------------------------------------
# 1. Check prerequisites
# -------------------------------------------
info "Checking prerequisites..."

if ! command -v docker &>/dev/null; then
  error "Docker is not installed. Install Docker Desktop from https://www.docker.com/products/docker-desktop/"
fi

if ! docker info &>/dev/null; then
  error "Docker daemon is not running. Please start Docker Desktop."
fi

if ! docker compose version &>/dev/null; then
  error "Docker Compose V2 is not available. Update Docker Desktop."
fi

DOCKER_VERSION=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo "unknown")
info "Docker version: $DOCKER_VERSION"

# Check available disk space (need at least 10GB)
if command -v df &>/dev/null; then
  AVAIL_KB=$(df -k . | tail -1 | awk '{print $4}')
  AVAIL_GB=$((AVAIL_KB / 1024 / 1024))
  if [ "$AVAIL_GB" -lt 10 ]; then
    warn "Low disk space: ${AVAIL_GB}GB available (recommend 10GB+)"
  else
    info "Disk space: ${AVAIL_GB}GB available"
  fi
fi

# Check available memory
if command -v sysctl &>/dev/null; then
  TOTAL_MEM_BYTES=$(sysctl -n hw.memsize 2>/dev/null || echo 0)
  TOTAL_MEM_GB=$((TOTAL_MEM_BYTES / 1024 / 1024 / 1024))
  if [ "$TOTAL_MEM_GB" -lt 8 ]; then
    warn "System has ${TOTAL_MEM_GB}GB RAM. Recommend 8GB+ for full stack."
  else
    info "System memory: ${TOTAL_MEM_GB}GB"
  fi
fi

# -------------------------------------------
# 2. Environment file
# -------------------------------------------
if [ ! -f .env ]; then
  info "Creating .env from .env.example..."
  cp .env.example .env
  warn ".env created with default values. Review and update secrets for production use."
else
  info ".env already exists, skipping."
fi

if [ ! -f .env.mail ]; then
  info "Creating empty .env.mail (mail OAuth credentials)..."
  cat > .env.mail <<'ENVMAIL'
# Mail Automation OAuth (optional)
# ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID=
# ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_SECRET=
# ECM_MAIL_OAUTH_GMAIL_JOSHUA_REFRESH_TOKEN=
ENVMAIL
  warn ".env.mail created with placeholders. Fill in if mail automation is needed."
else
  info ".env.mail already exists, skipping."
fi

# -------------------------------------------
# 3. Create required directories
# -------------------------------------------
info "Ensuring required directories exist..."
mkdir -p init-scripts odoo/config odoo/addons \
         monitoring/grafana/provisioning/datasources \
         monitoring/grafana/provisioning/dashboards \
         monitoring/grafana/dashboards \
         nginx/ssl nginx/logs logs

# -------------------------------------------
# 4. Pull / Build images
# -------------------------------------------
if [ "$CORE_ONLY" = true ]; then
  SERVICES="postgres postgres-keycloak keycloak redis elasticsearch minio rabbitmq ecm-core ecm-frontend"
  info "Core-only mode: will start essential services only."
else
  SERVICES=""
  info "Full-stack mode: all services."
fi

info "Pulling pre-built images..."
if [ "$CORE_ONLY" = true ]; then
  docker compose pull postgres postgres-keycloak keycloak redis elasticsearch minio rabbitmq 2>&1 || warn "Some image pulls failed — will retry on 'up'."
else
  docker compose pull 2>&1 || warn "Some image pulls failed — will retry on 'up'."
fi

if [ "$SKIP_BUILD" = false ]; then
  info "Building application images (ecm-core, ecm-frontend, ml-service)..."
  if [ "$CORE_ONLY" = true ]; then
    docker compose build ecm-core ecm-frontend 2>&1
  else
    docker compose build 2>&1
  fi
else
  info "Skipping image build (--skip-build)."
fi

# -------------------------------------------
# 5. Start services
# -------------------------------------------
info "Starting services..."
if [ "$CORE_ONLY" = true ]; then
  docker compose up -d $SERVICES
else
  docker compose up -d
fi

# -------------------------------------------
# 6. Wait for health checks
# -------------------------------------------
info "Waiting for services to become healthy..."

wait_for_health() {
  local service=$1
  local url=$2
  local max_wait=${3:-120}
  local elapsed=0

  while [ $elapsed -lt $max_wait ]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      info "  $service — healthy"
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  warn "  $service — not healthy after ${max_wait}s"
  return 1
}

HEALTHY=true

wait_for_health "PostgreSQL"     "localhost:${POSTGRES_PORT:-5432}" 60 2>/dev/null || \
  { docker compose exec -T postgres pg_isready -U ecm_user -d ecm_db >/dev/null 2>&1 && info "  PostgreSQL — healthy" || { warn "  PostgreSQL — not reachable"; HEALTHY=false; }; }

wait_for_health "Elasticsearch"  "http://localhost:${ELASTICSEARCH_PORT:-9200}/_cluster/health" 90 || HEALTHY=false
wait_for_health "Redis"          "" 0 2>/dev/null || true  # Redis has no HTTP; check via docker
docker compose exec -T redis redis-cli -a "${REDIS_PASSWORD:-redis_password}" ping >/dev/null 2>&1 && info "  Redis — healthy" || { warn "  Redis — not reachable"; HEALTHY=false; }
wait_for_health "MinIO"          "http://localhost:${MINIO_PORT:-9205}/minio/health/live" 60 || HEALTHY=false
wait_for_health "Keycloak"       "http://localhost:${KEYCLOAK_PORT:-8180}" 120 || HEALTHY=false
wait_for_health "ECM Core"       "http://localhost:${ECM_API_PORT:-7700}/actuator/health" 180 || HEALTHY=false
wait_for_health "ECM Frontend"   "http://localhost:${ECM_FRONTEND_PORT:-5500}" 60 || HEALTHY=false

echo ""
if [ "$HEALTHY" = true ]; then
  info "============================================"
  info "  Athena ECM is ready!"
  info "============================================"
  info "  Frontend:    http://localhost:${ECM_FRONTEND_PORT:-5500}"
  info "  API:         http://localhost:${ECM_API_PORT:-7700}"
  info "  Keycloak:    http://localhost:${KEYCLOAK_PORT:-8180}"
  info "  MinIO:       http://localhost:${MINIO_CONSOLE_PORT:-9206}"
  info "  RabbitMQ:    http://localhost:15672"
  info "  Grafana:     http://localhost:3001"
  info "  Prometheus:  http://localhost:9090"
  info "============================================"
else
  warn "============================================"
  warn "  Some services are not healthy."
  warn "  Run 'docker compose ps' to check status."
  warn "  Run 'docker compose logs <service>' for details."
  warn "============================================"
fi
