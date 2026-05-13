#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

MAVEN_BIN="${MAVEN_BIN:-}"
if [[ -z "${MAVEN_BIN}" ]]; then
  if [[ -x "/tmp/apache-maven-3.9.9/bin/mvn" ]]; then
    MAVEN_BIN="/tmp/apache-maven-3.9.9/bin/mvn"
  elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
  else
    echo "property_encryption_closeout_preflight: Maven not found. Set MAVEN_BIN=/path/to/mvn." >&2
    exit 1
  fi
fi
export MAVEN_BIN

BACKEND_NON_DOCKER_TESTS="${BACKEND_NON_DOCKER_TESTS:-PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest,PropertyEncryptionAsyncTaskServiceTest,AsyncTaskGovernanceServiceTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest,NodePropertyEncryptionServiceTest,NodeControllerAspectTest,DocumentControllerCheckoutTest,ContentTypeControllerPreviewSemanticsTest,SearchIndexServiceSubtreeReindexTest}"
FRONTEND_TEST_PATHS="${FRONTEND_TEST_PATHS:-src/services/propertyEncryptionService.test.ts src/pages/PropertyEncryptionOperationsPage.test.tsx src/utils/propertyRedactionUtils.test.ts}"
RUN_FRONTEND_BUILD="${RUN_FRONTEND_BUILD:-1}"
RUN_PHASE5_REGISTRY="${RUN_PHASE5_REGISTRY:-1}"
RUN_DOCKER_BACKED_GATE="${RUN_DOCKER_BACKED_GATE:-1}"
REQUIRE_DOCKER_BACKED_GATE="${REQUIRE_DOCKER_BACKED_GATE:-0}"

echo "property_encryption_closeout_preflight: start"
echo "MAVEN_BIN=${MAVEN_BIN}"
echo "BACKEND_NON_DOCKER_TESTS=${BACKEND_NON_DOCKER_TESTS}"
echo "FRONTEND_TEST_PATHS=${FRONTEND_TEST_PATHS}"
echo "RUN_FRONTEND_BUILD=${RUN_FRONTEND_BUILD}"
echo "RUN_PHASE5_REGISTRY=${RUN_PHASE5_REGISTRY}"
echo "RUN_DOCKER_BACKED_GATE=${RUN_DOCKER_BACKED_GATE}"
echo "REQUIRE_DOCKER_BACKED_GATE=${REQUIRE_DOCKER_BACKED_GATE}"

echo "property_encryption_closeout_preflight: check script syntax"
bash -n scripts/property-encryption-backfill-gate.sh
bash -n scripts/phase5-regression.sh

echo "property_encryption_closeout_preflight: check whitespace"
git diff --check

echo "property_encryption_closeout_preflight: backend non-Docker evidence"
(
  cd ecm-core
  "${MAVEN_BIN}" -B -Dstyle.color=never \
    -Dmaven.repo.local=.m2-cache/repository \
    -Dspring.profiles.active=test \
    -Dtest="${BACKEND_NON_DOCKER_TESTS}" \
    test
)

echo "property_encryption_closeout_preflight: frontend targeted evidence"
(
  cd ecm-frontend
  # shellcheck disable=SC2086
  CI=true npm test -- --runTestsByPath ${FRONTEND_TEST_PATHS} --watchAll=false
)

echo "property_encryption_closeout_preflight: frontend lint"
(
  cd ecm-frontend
  npm run lint
)

if [[ "${RUN_FRONTEND_BUILD}" == "1" ]]; then
  echo "property_encryption_closeout_preflight: frontend production build"
  (
    cd ecm-frontend
    CI=true npm run build
  )
fi

if [[ "${RUN_PHASE5_REGISTRY}" == "1" ]]; then
  echo "property_encryption_closeout_preflight: Phase 5 registry-only preflight"
  PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh
fi

if [[ "${RUN_DOCKER_BACKED_GATE}" == "1" ]]; then
  echo "property_encryption_closeout_preflight: Docker-backed PostgreSQL gate precheck"
  if docker ps >/dev/null 2>&1; then
    echo "property_encryption_closeout_preflight: Docker reachable; running scripts/property-encryption-backfill-gate.sh"
    bash scripts/property-encryption-backfill-gate.sh
    echo "property_encryption_closeout_preflight: Docker-backed gate passed"
  else
    echo "property_encryption_closeout_preflight: Docker-backed gate BLOCKED because Docker API is not reachable." >&2
    if [[ "${REQUIRE_DOCKER_BACKED_GATE}" == "1" ]]; then
      docker ps >/dev/null
    fi
    echo "property_encryption_closeout_preflight: continuing because REQUIRE_DOCKER_BACKED_GATE=0"
  fi
else
  echo "property_encryption_closeout_preflight: Docker-backed gate skipped by RUN_DOCKER_BACKED_GATE=0"
fi

echo "property_encryption_closeout_preflight: ok"
