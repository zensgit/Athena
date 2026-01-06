#!/usr/bin/env bash
set -euo pipefail

# One-click verification script for ECM system.
# Runs all validation steps and consolidates logs in tmp/
#
# Usage:
#   ./scripts/verify.sh              # Full verification (restart services)
#   ./scripts/verify.sh --no-restart # Skip service restart (faster)
#   ./scripts/verify.sh --smoke-only # Only run smoke tests (no E2E)
#
# Prerequisites:
#   - Docker and docker-compose installed
#   - Node.js and npm installed
#   - jq installed (for JSON parsing)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"
ORIGINAL_ARGS="$*"

# Parse arguments
SKIP_RESTART=0
SMOKE_ONLY=0
SKIP_BUILD=0
WOPI_ONLY=0
SKIP_WOPI=0
for arg in "$@"; do
  case "$arg" in
    --no-restart) SKIP_RESTART=1 ;;
    --smoke-only) SMOKE_ONLY=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --wopi-only) WOPI_ONLY=1 ;;
    --skip-wopi) SKIP_WOPI=1 ;;
    --help|-h)
      echo "Usage: $0 [--no-restart] [--smoke-only] [--skip-build]"
      echo "  --no-restart  Skip docker-compose restart (services must be running)"
      echo "  --smoke-only  Only run API smoke tests, skip E2E tests"
      echo "  --skip-build  Skip frontend build step"
      echo "  --wopi-only   Only run WOPI verification (skip other steps)"
      echo "  --skip-wopi   Skip WOPI verification step"
      exit 0
      ;;
  esac
done

# If wopi-only is enabled, skip all other steps.
if [[ ${WOPI_ONLY} -eq 1 ]]; then
  SKIP_RESTART=1
  SMOKE_ONLY=1
  SKIP_BUILD=1
fi

# Load environment
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}"
ECM_API_URL="${ECM_API_URL:-http://localhost:${ECM_API_PORT:-7700}}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecm}"
ECM_FRONTEND_URL="${ECM_FRONTEND_URL:-http://localhost:${ECM_FRONTEND_PORT:-5500}}"
ECM_VERIFY_USER="${ECM_VERIFY_USER:-${KEYCLOAK_USER:-admin}}"
ECM_VERIFY_PASS="${ECM_VERIFY_PASS:-${KEYCLOAK_PASSWORD:-admin}}"
FRONTEND_DIR="${REPO_ROOT}/ecm-frontend"
LOG_DIR="${REPO_ROOT}/tmp"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
REPORT_FILE="${LOG_DIR}/${TIMESTAMP}_verify-report.md"
WOPI_SUMMARY_FILE="${LOG_DIR}/${TIMESTAMP}_verify-wopi.summary.log"
FRONTEND_DEPS_INSTALLED=0
NPM_CACHE_DIR="${REPO_ROOT}/tmp/npm-cache"

# Create log directory
mkdir -p "${LOG_DIR}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Track step results
STEPS_PASSED=0
STEPS_FAILED=0
STEPS_SKIPPED=0

write_wopi_summary() {
  local status="$1"
  local log_file="$2"
  {
    echo "verify-wopi status: ${status}"
    echo "log: ${log_file}"
    if [[ -f "${log_file}" ]]; then
      echo ""
      echo "verify-wopi output:"
      grep -E '^\[verify\]' "${log_file}" || true
    fi
  } > "${WOPI_SUMMARY_FILE}"
}

write_verification_report() {
  local status="PASSED"
  if [[ ${STEPS_FAILED} -gt 0 ]]; then
    status="FAILED"
  fi

  {
    echo "# Verification Report (${TIMESTAMP})"
    echo ""
    echo "## Command"
    if [[ -n "${ORIGINAL_ARGS}" ]]; then
      echo "- $0 ${ORIGINAL_ARGS}"
    else
      echo "- $0"
    fi
    echo ""
    echo "## Results"
    echo "- Passed: ${STEPS_PASSED}"
    echo "- Failed: ${STEPS_FAILED}"
    echo "- Skipped: ${STEPS_SKIPPED}"
    echo "- Status: ${status}"
    echo ""
    echo "## Artifacts"
    echo "- Logs prefix: ${LOG_DIR}/${TIMESTAMP}_*"
    if [[ -f "${WOPI_SUMMARY_FILE}" ]]; then
      echo "- WOPI summary: ${WOPI_SUMMARY_FILE}"
    fi
  } > "${REPORT_FILE}"
}

trap write_verification_report EXIT

run_step() {
  local step_name="$1"
  local log_file="${LOG_DIR}/${TIMESTAMP}_${step_name}.log"
  shift

  log_info "Running: ${step_name}..."

  if "$@" > "${log_file}" 2>&1; then
    log_success "${step_name} passed (log: ${log_file})"
    ((STEPS_PASSED+=1))
    if [[ "${step_name}" == "verify-wopi" ]]; then
      write_wopi_summary "passed" "${log_file}"
    fi
    return 0
  else
    local exit_code=$?
    log_error "${step_name} failed (exit code: ${exit_code}, log: ${log_file})"
    ((STEPS_FAILED+=1))
    if [[ "${step_name}" == "verify-wopi" ]]; then
      write_wopi_summary "failed" "${log_file}"
    fi
    return ${exit_code}
  fi
}

run_step_optional() {
  local step_name="$1"
  local log_file="${LOG_DIR}/${TIMESTAMP}_${step_name}.log"
  shift

  log_info "Running: ${step_name}..."

  if "$@" > "${log_file}" 2>&1; then
    log_success "${step_name} passed (log: ${log_file})"
    ((STEPS_PASSED+=1))
    return 0
  else
    local exit_code=$?
    log_warn "${step_name} failed (non-critical, exit code: ${exit_code}, log: ${log_file})"
    ((STEPS_SKIPPED+=1))
    return 0
  fi
}

docker_compose() {
  if command -v docker-compose >/dev/null 2>&1; then
    (cd "${REPO_ROOT}" && docker-compose -f docker-compose.yml "$@")
  else
    docker compose --project-directory "${REPO_ROOT}" -f "${REPO_ROOT}/docker-compose.yml" "$@"
  fi
}

wait_for_service() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-30}"
  local attempt=1

  log_info "Waiting for ${name} at ${url}..."

  while [[ ${attempt} -le ${max_attempts} ]]; do
    if curl -fsS -o /dev/null "${url}" 2>/dev/null; then
      log_success "${name} is ready"
      return 0
    fi
    echo -n "."
    sleep 2
    ((attempt++))
  done

  echo ""
  log_error "${name} did not become ready after $((max_attempts * 2)) seconds"
  return 1
}

wait_for_clamav_health() {
  local container_id status
  for _ in {1..30}; do
    container_id="$(docker_compose ps -q clamav 2>/dev/null || true)"
    if [[ -z "${container_id}" ]]; then
      echo "ClamAV service not running; skipping."
      return 0
    fi

    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "${container_id}" 2>/dev/null || true)"
    if [[ "${status}" == "healthy" ]]; then
      echo "ClamAV healthy."
      return 0
    fi
    if [[ "${status}" == "no-healthcheck" || -z "${status}" ]]; then
      echo "ClamAV healthcheck not available; skipping."
      return 0
    fi
    sleep 2
  done
  return 1
}

ensure_clamav_health() {
  local container_id status
  container_id="$(docker_compose ps -q clamav 2>/dev/null || true)"
  if [[ -z "${container_id}" ]]; then
    echo "ClamAV service not running; skipping."
    return 0
  fi

  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "${container_id}" 2>/dev/null || true)"
  if [[ "${status}" == "healthy" ]]; then
    echo "ClamAV healthy."
    return 0
  fi
  if [[ "${status}" == "no-healthcheck" || -z "${status}" ]]; then
    echo "ClamAV healthcheck not available; skipping."
    return 0
  fi

  echo "ClamAV status=${status}; waiting for healthy..."
  if wait_for_clamav_health; then
    return 0
  fi

  echo "ClamAV still not healthy; restarting..."
  docker_compose restart clamav
  if wait_for_clamav_health; then
    return 0
  fi

  echo "ClamAV still unhealthy after restart."
  return 1
}

ensure_frontend_deps() {
  if [[ "${FRONTEND_DEPS_INSTALLED}" -eq 1 ]]; then
    return 0
  fi

  if [[ -f package-lock.json ]]; then
    mkdir -p "${NPM_CACHE_DIR}"
    run_step "frontend-install" npm ci --legacy-peer-deps --no-audit --no-fund --cache "${NPM_CACHE_DIR}"
  else
    log_warn "package-lock.json missing; falling back to npm install."
    mkdir -p "${NPM_CACHE_DIR}"
    run_step "frontend-install" npm install --legacy-peer-deps --no-audit --no-fund --cache "${NPM_CACHE_DIR}"
  fi
  FRONTEND_DEPS_INSTALLED=1
}

# ============================================================
# STEP 1: Restart services (optional)
# ============================================================
if [[ ${SKIP_RESTART} -eq 0 ]]; then
  log_info "=== Step 1: Restarting ECM services ==="
  if [[ -f "${SCRIPT_DIR}/restart-ecm.sh" ]]; then
    run_step "restart-ecm" bash "${SCRIPT_DIR}/restart-ecm.sh" || true
  else
    log_warn "restart-ecm.sh not found, skipping restart"
  fi
else
  log_info "=== Step 1: Skipping service restart (--no-restart) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# STEP 2: Wait for services to be healthy
# ============================================================
log_info "=== Step 2: Waiting for services ==="

# Wait for Keycloak
wait_for_service "Keycloak" "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration" 60 || {
  log_error "Keycloak not ready, aborting"
  exit 1
}

# Wait for ECM Core API
wait_for_service "ECM Core API" "${ECM_API_URL}/actuator/health" 60 || {
  log_error "ECM Core API not ready, aborting"
  exit 1
}

log_success "All services are ready"

# ============================================================
# STEP 2.5: Ensure ClamAV is healthy (optional)
# ============================================================
log_info "=== Step 2.5: Checking ClamAV health ==="
run_step_optional "clamav-health" ensure_clamav_health

# ============================================================
# STEP 3: Create test users in Keycloak
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 3: Creating test users in Keycloak ==="
  if [[ -f "${SCRIPT_DIR}/keycloak/create-test-users.sh" ]]; then
    run_step_optional "create-test-users" bash "${SCRIPT_DIR}/keycloak/create-test-users.sh"
  else
    log_warn "create-test-users.sh not found, skipping"
    ((STEPS_SKIPPED+=1))
  fi
else
  log_info "=== Step 3: Skipping test user creation (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# STEP 4: Get access token
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 4: Getting access token ==="
  if [[ -f "${SCRIPT_DIR}/get-token.sh" ]]; then
    run_step "get-token-admin" bash "${SCRIPT_DIR}/get-token.sh" admin admin
    run_step "get-token-viewer" bash "${SCRIPT_DIR}/get-token.sh" viewer viewer
  else
    log_warn "get-token.sh not found, skipping"
    ((STEPS_SKIPPED+=1))
  fi
else
  log_info "=== Step 4: Skipping token fetch (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# STEP 5: Run API smoke tests
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 5: Running API smoke tests ==="
  if [[ -f "${SCRIPT_DIR}/smoke.sh" ]]; then
    run_step "smoke-test" env \
      ECM_API="${ECM_API_URL}" \
      ECM_TOKEN_FILE="${REPO_ROOT}/tmp/admin.access_token" \
      bash "${SCRIPT_DIR}/smoke.sh"
  else
    log_error "smoke.sh not found!"
    ((STEPS_FAILED+=1))
  fi
else
  log_info "=== Step 5: Skipping API smoke tests (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# STEP 5.5: Phase C security verification (ACL + share links)
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 5.5: Running security verification (Phase C) ==="
  if [[ -f "${SCRIPT_DIR}/verify-phase-c.py" ]]; then
    run_step "verify-phase-c" env \
      API_BASE_URL="${ECM_API_URL}" \
      python3 "${SCRIPT_DIR}/verify-phase-c.py"
  else
    log_warn "verify-phase-c.py not found, skipping"
    ((STEPS_SKIPPED+=1))
  fi
else
  log_info "=== Step 5.5: Skipping security verification (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# STEP 6: Build frontend (optional)
# ============================================================
if [[ ${SKIP_BUILD} -eq 0 ]]; then
  log_info "=== Step 6: Building frontend ==="
  if [[ -d "${FRONTEND_DIR}" ]]; then
    cd "${FRONTEND_DIR}"
    ensure_frontend_deps
    run_step "frontend-build" npm run build
    cd "${REPO_ROOT}"
  else
    log_error "Frontend directory not found: ${FRONTEND_DIR}"
    ((STEPS_FAILED+=1))
  fi
else
  log_info "=== Step 6: Skipping frontend build (--skip-build) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# STEP 6.5: Verify WOPI preview/edit + audit (optional)
# ============================================================
if [[ ${SKIP_WOPI} -eq 0 ]]; then
  log_info "=== Step 6.5: Verifying WOPI preview + audit ==="
  if [[ -f "${SCRIPT_DIR}/verify-wopi.js" ]]; then
    if [[ -d "${FRONTEND_DIR}" ]]; then
      cd "${FRONTEND_DIR}"
      ensure_frontend_deps
      if ! npx playwright --version > /dev/null 2>&1; then
        log_info "Installing Playwright browsers..."
        npx playwright install chromium > /dev/null 2>&1 || true
      fi
      cd "${REPO_ROOT}"
    fi
    run_step "verify-wopi" env \
      ECM_FRONTEND_URL="${ECM_FRONTEND_URL}" \
      ECM_API_URL="${ECM_API_URL}" \
      KEYCLOAK_URL="${KEYCLOAK_URL}" \
      KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
      ECM_VERIFY_USER="${ECM_VERIFY_USER}" \
      ECM_VERIFY_PASS="${ECM_VERIFY_PASS}" \
      node "${SCRIPT_DIR}/verify-wopi.js"
  else
    log_warn "verify-wopi.js not found, skipping"
    ((STEPS_SKIPPED+=1))
  fi
else
  log_info "=== Step 6.5: Skipping WOPI verification (--skip-wopi) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# STEP 7: Run E2E tests (optional)
# ============================================================
if [[ ${SMOKE_ONLY} -eq 0 ]]; then
  log_info "=== Step 7: Running E2E tests ==="
  if [[ -d "${FRONTEND_DIR}/e2e" ]]; then
    cd "${FRONTEND_DIR}"
    ensure_frontend_deps
    # Install Playwright browsers if needed
    if ! npx playwright --version > /dev/null 2>&1; then
      log_info "Installing Playwright browsers..."
      npx playwright install chromium > /dev/null 2>&1 || true
    fi

    run_step "e2e-test" npx playwright test --project=chromium
    cd "${REPO_ROOT}"
  else
    log_warn "E2E tests directory not found, skipping"
    ((STEPS_SKIPPED+=1))
  fi
else
  log_info "=== Step 7: Skipping E2E tests (--smoke-only) ==="
  ((STEPS_SKIPPED+=1))
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================================"
echo "VERIFICATION SUMMARY"
echo "============================================================"
echo -e "  ${GREEN}Passed:${NC}  ${STEPS_PASSED}"
echo -e "  ${RED}Failed:${NC}  ${STEPS_FAILED}"
echo -e "  ${YELLOW}Skipped:${NC} ${STEPS_SKIPPED}"
echo ""
echo "Logs directory: ${LOG_DIR}"
echo "Log files prefix: ${TIMESTAMP}_*"
echo ""

if [[ ${STEPS_FAILED} -gt 0 ]]; then
  log_error "Verification FAILED (${STEPS_FAILED} step(s) failed)"
  exit 1
else
  log_success "Verification PASSED"
  exit 0
fi
