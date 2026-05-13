#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

MAVEN_BIN="${MAVEN_BIN:-}"
if [[ -z "${MAVEN_BIN}" ]]; then
  if [[ -x "/tmp/codex-maven/apache-maven-3.9.11/bin/mvn" ]]; then
    MAVEN_BIN="/tmp/codex-maven/apache-maven-3.9.11/bin/mvn"
  elif [[ -x "/tmp/apache-maven-3.9.9/bin/mvn" ]]; then
    MAVEN_BIN="/tmp/apache-maven-3.9.9/bin/mvn"
  elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
  else
    echo "property_encryption_async_governance_gate: Maven not found. Set MAVEN_BIN=/path/to/mvn." >&2
    exit 1
  fi
fi

BACKEND_TESTS="${BACKEND_TESTS:-PropertyEncryptionAsyncTaskServiceTest,AsyncTaskGovernanceServiceTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest}"
FRONTEND_TEST_PATHS="${FRONTEND_TEST_PATHS:-src/pages/PropertyEncryptionOperationsPage.test.tsx}"
E2E_SPECS="${E2E_SPECS:-e2e/admin-property-encryption.mock.spec.ts e2e/admin-audit-filter-export.mock.spec.ts e2e/admin-async-governance-overview-fallback.mock.spec.ts}"
PW_PROJECT="${PW_PROJECT:-chromium}"
RUN_FRONTEND_BUILD="${RUN_FRONTEND_BUILD:-1}"
RUN_PHASE5_REGISTRY="${RUN_PHASE5_REGISTRY:-1}"
RUN_E2E="${RUN_E2E:-1}"
USE_EXISTING_UI="${USE_EXISTING_UI:-0}"
ECM_UI_URL="${ECM_UI_URL:-}"

srv_pid=""
srv_log=""

cleanup() {
  if [[ -n "${srv_pid}" ]]; then
    kill "${srv_pid}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

start_ephemeral_static_server() {
  if ! command -v npx >/dev/null 2>&1; then
    echo "property_encryption_async_governance_gate: npx not found; cannot start static UI server." >&2
    return 1
  fi

  srv_log="/tmp/property-encryption-async-governance.http.$$.$RANDOM.log"
  (
    cd ecm-frontend
    npx serve -s build -l 0 >"${srv_log}" 2>&1
  ) &
  srv_pid=$!

  local discovered_url=""
  for _ in $(seq 1 80); do
    if [[ -f "${srv_log}" ]]; then
      discovered_url="$(sed -nE 's#.*(http://localhost:[0-9]+).*#\1#p' "${srv_log}" | tail -n 1)"
    fi
    if [[ -n "${discovered_url}" ]] && curl -fsS --max-time 1 "${discovered_url}" >/dev/null 2>&1; then
      ECM_UI_URL="${discovered_url}"
      export ECM_UI_URL
      return 0
    fi
    if ! kill -0 "${srv_pid}" >/dev/null 2>&1; then
      cat "${srv_log}" >&2 || true
      return 1
    fi
    sleep 0.25
  done

  cat "${srv_log}" >&2 || true
  echo "property_encryption_async_governance_gate: timed out waiting for static UI server." >&2
  return 1
}

echo "property_encryption_async_governance_gate: start"
echo "MAVEN_BIN=${MAVEN_BIN}"
echo "BACKEND_TESTS=${BACKEND_TESTS}"
echo "FRONTEND_TEST_PATHS=${FRONTEND_TEST_PATHS}"
echo "E2E_SPECS=${E2E_SPECS}"
echo "PW_PROJECT=${PW_PROJECT}"
echo "RUN_FRONTEND_BUILD=${RUN_FRONTEND_BUILD}"
echo "RUN_PHASE5_REGISTRY=${RUN_PHASE5_REGISTRY}"
echo "RUN_E2E=${RUN_E2E}"
echo "USE_EXISTING_UI=${USE_EXISTING_UI}"

echo "property_encryption_async_governance_gate: check script syntax"
bash -n scripts/property-encryption-async-governance-gate.sh
bash -n scripts/property-encryption-closeout-preflight.sh
bash -n scripts/phase5-regression.sh

echo "property_encryption_async_governance_gate: check whitespace"
git diff --check -- . ':!.env'

echo "property_encryption_async_governance_gate: backend async-governance contract tests"
(
  cd ecm-core
  "${MAVEN_BIN}" -B -Dstyle.color=never \
    -Dmaven.repo.local=.m2-cache/repository \
    -Dspring.profiles.active=test \
    -Dtest="${BACKEND_TESTS}" \
    test
)

echo "property_encryption_async_governance_gate: frontend targeted Jest"
(
  cd ecm-frontend
  # shellcheck disable=SC2086
  CI=true npm test -- --runTestsByPath ${FRONTEND_TEST_PATHS} --watchAll=false
)

echo "property_encryption_async_governance_gate: frontend lint"
(
  cd ecm-frontend
  npm run lint
)

if [[ "${RUN_FRONTEND_BUILD}" == "1" ]]; then
  echo "property_encryption_async_governance_gate: frontend production build"
  (
    cd ecm-frontend
    CI=true npm run build
  )
fi

if [[ "${RUN_PHASE5_REGISTRY}" == "1" ]]; then
  echo "property_encryption_async_governance_gate: Phase 5 registry-only preflight"
  PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh
fi

if [[ "${RUN_E2E}" == "1" ]]; then
  if [[ "${USE_EXISTING_UI}" == "1" ]]; then
    if [[ -z "${ECM_UI_URL}" ]]; then
      echo "property_encryption_async_governance_gate: USE_EXISTING_UI=1 requires ECM_UI_URL." >&2
      exit 1
    fi
    curl -fsS --max-time 3 "${ECM_UI_URL}" >/dev/null
  else
    start_ephemeral_static_server
  fi

  echo "property_encryption_async_governance_gate: mocked E2E against ${ECM_UI_URL}"
  (
    cd ecm-frontend
    # shellcheck disable=SC2086
    ECM_UI_URL="${ECM_UI_URL}" npx playwright test ${E2E_SPECS} --project="${PW_PROJECT}"
  )
fi

echo "property_encryption_async_governance_gate: ok"
