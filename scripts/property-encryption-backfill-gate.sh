#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

BACKEND_TESTS="${BACKEND_TESTS:-NodeRepositoryJsonbBackfillSmokeTest,PropertyEncryptionBackfillJobRepositoryTest,PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest,PropertyEncryptionAsyncConfigurationTest,PropertyEncryptionBackfillRunnerTest,PropertyEncryptionBackfillRunnerAsyncProxyTest,PropertyEncryptionBackfillRecoverySchedulerTest}"
DOCKER_CHECK_TIMEOUT_SECONDS="${DOCKER_CHECK_TIMEOUT_SECONDS:-5}"

echo "property_encryption_backfill_gate: start"
echo "BACKEND_TESTS=${BACKEND_TESTS}"
echo "DOCKER_CHECK_TIMEOUT_SECONDS=${DOCKER_CHECK_TIMEOUT_SECONDS}"

if ! timeout "${DOCKER_CHECK_TIMEOUT_SECONDS}" docker ps >/dev/null 2>&1; then
  echo "property_encryption_backfill_gate: Docker API is not reachable; this gate requires Docker because ecm-core/mvnw and Testcontainers depend on it." >&2
  docker ps >/dev/null
fi

echo "property_encryption_backfill_gate: backend targeted tests"
(
  cd ecm-core
  ./mvnw -B -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest="${BACKEND_TESTS}" test
)

echo "property_encryption_backfill_gate: ok"
