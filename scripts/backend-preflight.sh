#!/usr/bin/env bash
#
# backend-preflight.sh — run an ecm-core Maven goal locally WITHOUT Docker when a host
# Maven binary is available, falling back clearly to the Docker-backed ./mvnw wrapper.
#
# Motivation: ecm-core/mvnw is a Docker launcher, so on dev boxes without a reachable Docker
# daemon, Java testCompile / Mockito strict-stub / fixture-drift mistakes only surface in CI.
# This helper lets those be caught locally in seconds when a non-Docker Maven is present.
#
# Usage:
#   scripts/backend-preflight.sh                       # defaults to: test-compile
#   scripts/backend-preflight.sh test-compile
#   scripts/backend-preflight.sh -Dtest=SavedSearchServiceCsvExportTest test
#   MAVEN_BIN=/path/to/mvn scripts/backend-preflight.sh test-compile
#
# Maven resolution order (first hit wins):
#   1. $MAVEN_BIN (if executable)
#   2. /tmp/codex-maven/apache-maven-3.9.11/bin/mvn
#   3. /tmp/apache-maven-3.9.9/bin/mvn
#   4. mvn on PATH
#   5. ecm-core/mvnw  (Docker-backed; last fallback)
#
# Out of scope: does not install Maven, does not modify CI, does not replace ./mvnw.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Injected unless the caller overrides via env. Last-on-command-line wins in Maven, so a
# caller passing its own -D flags still takes precedence.
MAVEN_REPO_LOCAL_ARG="${MAVEN_REPO_LOCAL:-.m2-cache/repository}"
SPRING_PROFILE_ARG="${SPRING_PROFILES_ACTIVE:-test}"

# Goal(s): default to test-compile (cheapest catch of the dominant failure class) when none given.
if [[ "$#" -eq 0 ]]; then
  set -- test-compile
fi

resolve_host_maven() {
  if [[ -n "${MAVEN_BIN:-}" && -x "${MAVEN_BIN}" ]]; then
    printf '%s\n' "${MAVEN_BIN}"
    return 0
  fi
  local candidate
  for candidate in \
    "/tmp/codex-maven/apache-maven-3.9.11/bin/mvn" \
    "/tmp/apache-maven-3.9.9/bin/mvn"; do
    if [[ -x "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done
  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return 0
  fi
  return 1
}

cd "${ROOT_DIR}/ecm-core"

if HOST_MVN="$(resolve_host_maven)"; then
  echo "backend-preflight: using host Maven at ${HOST_MVN}"
  echo "backend-preflight: goal(s) = $*"
  exec "${HOST_MVN}" -B -Dstyle.color=never \
    -Dmaven.repo.local="${MAVEN_REPO_LOCAL_ARG}" \
    -Dspring.profiles.active="${SPRING_PROFILE_ARG}" \
    "$@"
fi

echo "backend-preflight: no host Maven found (MAVEN_BIN unset, no /tmp Maven, no mvn on PATH)." >&2
echo "backend-preflight: falling back to the Docker-backed ./mvnw wrapper, which needs a running Docker daemon." >&2
echo "backend-preflight: to verify locally WITHOUT Docker, point MAVEN_BIN at a Maven install and re-run, e.g.:" >&2
echo "  export MAVEN_BIN=/tmp/apache-maven-3.9.9/bin/mvn" >&2

if [[ -x "./mvnw" ]]; then
  # Preserve the wrapper's actual error (e.g. the Docker-socket failure) for the operator.
  exec ./mvnw -B -Dstyle.color=never \
    -Dmaven.repo.local="${MAVEN_REPO_LOCAL_ARG}" \
    -Dspring.profiles.active="${SPRING_PROFILE_ARG}" \
    "$@"
fi

echo "backend-preflight: ./mvnw not found either; cannot run backend verification." >&2
exit 1
