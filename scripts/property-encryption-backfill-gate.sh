#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

BACKEND_TESTS="${BACKEND_TESTS:-NodeRepositoryJsonbBackfillSmokeTest,PropertyEncryptionBackfillPostgresIntegrationTest,PropertyEncryptionBackfillJobRepositoryTest,PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest,PropertyEncryptionAsyncConfigurationTest,PropertyEncryptionBackfillRunnerTest,PropertyEncryptionBackfillRunnerAsyncProxyTest,PropertyEncryptionBackfillRecoverySchedulerTest}"
DOCKER_CHECK_TIMEOUT_SECONDS="${DOCKER_CHECK_TIMEOUT_SECONDS:-5}"
MAVEN_BIN="${MAVEN_BIN:-}"
if [[ -z "${MAVEN_BIN}" ]]; then
  if [[ -x "/tmp/apache-maven-3.9.9/bin/mvn" ]]; then
    MAVEN_BIN="/tmp/apache-maven-3.9.9/bin/mvn"
  elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
  else
    MAVEN_BIN="./mvnw"
  fi
fi

echo "property_encryption_backfill_gate: start"
echo "BACKEND_TESTS=${BACKEND_TESTS}"
echo "DOCKER_CHECK_TIMEOUT_SECONDS=${DOCKER_CHECK_TIMEOUT_SECONDS}"
echo "MAVEN_BIN=${MAVEN_BIN}"

if ! timeout "${DOCKER_CHECK_TIMEOUT_SECONDS}" docker ps >/dev/null 2>&1; then
  echo "property_encryption_backfill_gate: Docker API is not reachable; this gate requires Docker because Testcontainers depend on it." >&2
  docker ps >/dev/null
fi

echo "property_encryption_backfill_gate: backend targeted tests"
(
  cd ecm-core
  "${MAVEN_BIN}" -B -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest="${BACKEND_TESTS}" test
)

echo "property_encryption_backfill_gate: ok"
