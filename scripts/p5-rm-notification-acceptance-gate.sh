#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

ECM_API_URL="${ECM_API_URL:-http://localhost:7700}"
ECM_UI_URL="${ECM_UI_URL:-http://localhost:3000}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecm}"
PW_PROJECT="${PW_PROJECT:-chromium}"
PW_WORKERS="${PW_WORKERS:-1}"
CHECK_RETRIES="${CHECK_RETRIES:-12}"
CHECK_SLEEP_SECONDS="${CHECK_SLEEP_SECONDS:-5}"
CURL_TIMEOUT_SECONDS="${CURL_TIMEOUT_SECONDS:-5}"

BACKEND_TESTS="${BACKEND_TESTS:-RmReportPresetDeliveryServiceTest,RmReportPresetControllerTest,RmReportPresetControllerSecurityTest,ActivityServiceTest,NotificationInboxServiceTest}"

wait_for_url() {
  local label="$1"
  local url="$2"
  local attempt=1

  while (( attempt <= CHECK_RETRIES )); do
    if curl -fsS --max-time "${CURL_TIMEOUT_SECONDS}" "${url}" >/dev/null; then
      echo "p5_rm_notification_acceptance_gate: ${label} reachable (${attempt}/${CHECK_RETRIES})"
      return 0
    fi

    if (( attempt == CHECK_RETRIES )); then
      break
    fi

    echo "p5_rm_notification_acceptance_gate: waiting for ${label} (${attempt}/${CHECK_RETRIES}) at ${url}" >&2
    sleep "${CHECK_SLEEP_SECONDS}"
    attempt=$((attempt + 1))
  done

  echo "p5_rm_notification_acceptance_gate: ${label} did not become reachable at ${url}" >&2
  curl -fsS --max-time "${CURL_TIMEOUT_SECONDS}" "${url}" >/dev/null
}

echo "p5_rm_notification_acceptance_gate: start"
echo "ECM_API_URL=${ECM_API_URL}"
echo "ECM_UI_URL=${ECM_UI_URL}"
echo "KEYCLOAK_URL=${KEYCLOAK_URL} KEYCLOAK_REALM=${KEYCLOAK_REALM}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"
echo "CHECK_RETRIES=${CHECK_RETRIES} CHECK_SLEEP_SECONDS=${CHECK_SLEEP_SECONDS} CURL_TIMEOUT_SECONDS=${CURL_TIMEOUT_SECONDS}"
echo "BACKEND_TESTS=${BACKEND_TESTS}"

echo "p5_rm_notification_acceptance_gate: backend targeted tests"
(
  cd ecm-core
  ./mvnw -B -Dstyle.color=never test -Dtest="${BACKEND_TESTS}"
)

echo "p5_rm_notification_acceptance_gate: check backend health"
wait_for_url "backend health" "${ECM_API_URL}/actuator/health"

echo "p5_rm_notification_acceptance_gate: check keycloak discovery"
wait_for_url "keycloak discovery" "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration"

echo "p5_rm_notification_acceptance_gate: check UI reachable"
wait_for_url "UI" "${ECM_UI_URL}"

echo "p5_rm_notification_acceptance_gate: run Playwright RM notification acceptance"
(
  cd ecm-frontend
  ECM_API_URL="${ECM_API_URL}" \
  ECM_UI_URL="${ECM_UI_URL}" \
  KEYCLOAK_URL="${KEYCLOAK_URL}" \
  KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
  npm run e2e:rm-notification:acceptance -- --project="${PW_PROJECT}" --workers="${PW_WORKERS}"
)

echo "p5_rm_notification_acceptance_gate: ok"
