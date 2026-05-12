#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."

MAVEN_BIN="${MAVEN_BIN:-/tmp/apache-maven-3.9.9/bin/mvn}"

if [[ ! -x "${MAVEN_BIN}" ]]; then
  echo "oauth_credential_admin_preflight: Maven binary not found or not executable at '${MAVEN_BIN}'." >&2
  echo "oauth_credential_admin_preflight: Set MAVEN_BIN to a valid mvn binary path, e.g.:" >&2
  echo "  MAVEN_BIN=/usr/local/bin/mvn bash scripts/oauth-credential-admin-preflight.sh" >&2
  exit 1
fi

BACKEND_TESTS="OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest"
FRONTEND_TEST_PATHS="src/services/oauthCredentialAdminService.test.ts src/pages/OAuthCredentialAdminPage.test.tsx src/components/layout/MainLayout.menu.test.tsx"

echo "oauth_credential_admin_preflight: start"
echo "MAVEN_BIN=${MAVEN_BIN}"
echo "BACKEND_TESTS=${BACKEND_TESTS}"
echo "FRONTEND_TEST_PATHS=${FRONTEND_TEST_PATHS}"

echo "oauth_credential_admin_preflight: step 1/3 backend targeted tests"
(
  cd ecm-core
  "${MAVEN_BIN}" -B -Dstyle.color=never \
    -Dmaven.repo.local=.m2-cache/repository \
    -Dspring.profiles.active=test \
    -Dtest="${BACKEND_TESTS}" \
    test
)

echo "oauth_credential_admin_preflight: step 2/3 frontend targeted tests"
(
  cd ecm-frontend
  # shellcheck disable=SC2086
  CI=true npm test -- --runTestsByPath ${FRONTEND_TEST_PATHS} --watchAll=false
)

echo "oauth_credential_admin_preflight: step 3/3 frontend lint and production build"
(
  cd ecm-frontend
  npm run lint
  CI=true npm run build
)

echo "oauth_credential_admin_preflight: ok"
