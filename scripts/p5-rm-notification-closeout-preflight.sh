#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

EXPECTED_ACCEPTANCE_TEST_COUNT="${EXPECTED_ACCEPTANCE_TEST_COUNT:-4}"
CI_WORKFLOW_FILE="${CI_WORKFLOW_FILE:-.github/workflows/ci.yml}"

echo "p5_rm_notification_closeout_preflight: start"
echo "EXPECTED_ACCEPTANCE_TEST_COUNT=${EXPECTED_ACCEPTANCE_TEST_COUNT}"
echo "CI_WORKFLOW_FILE=${CI_WORKFLOW_FILE}"

workflow_line_for() {
  local pattern="$1"
  local line

  line="$(rg -n -F -m 1 "${pattern}" "${CI_WORKFLOW_FILE}" | cut -d: -f1 || true)"
  if [[ -z "${line}" ]]; then
    echo "p5_rm_notification_closeout_preflight: missing CI workflow wiring: ${pattern}" >&2
    exit 1
  fi

  printf '%s' "${line}"
}

require_workflow_pattern() {
  local pattern="$1"

  workflow_line_for "${pattern}" >/dev/null
}

require_workflow_order() {
  local before="$1"
  local after="$2"
  local before_line
  local after_line

  before_line="$(workflow_line_for "${before}")"
  after_line="$(workflow_line_for "${after}")"

  if (( before_line >= after_line )); then
    echo "p5_rm_notification_closeout_preflight: expected '${before}' before '${after}' in ${CI_WORKFLOW_FILE} (lines ${before_line} >= ${after_line})" >&2
    exit 1
  fi
}

echo "p5_rm_notification_closeout_preflight: check workflow yaml"
ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0)); puts "yaml ok"' "${CI_WORKFLOW_FILE}"

echo "p5_rm_notification_closeout_preflight: check CI workflow wiring"
require_workflow_pattern "Run RM notification closeout preflight"
require_workflow_pattern "working-directory: ."
require_workflow_pattern "scripts/p5-rm-notification-closeout-preflight.sh"
require_workflow_pattern "Run RM notification acceptance gate"
require_workflow_pattern "bash scripts/p5-rm-notification-acceptance-gate.sh"
require_workflow_pattern "ecm-core/target/surefire-reports"
require_workflow_order "Install dependencies" "Run RM notification closeout preflight"
require_workflow_order "Run RM notification closeout preflight" "working-directory: ."
require_workflow_order "working-directory: ." "scripts/p5-rm-notification-closeout-preflight.sh"
require_workflow_order "scripts/p5-rm-notification-closeout-preflight.sh" "Lint"
require_workflow_order "Run RM notification closeout preflight" "Lint"
require_workflow_order "Wait for Keycloak realm" "Run RM notification acceptance gate"
require_workflow_order "Run RM notification acceptance gate" "bash scripts/p5-rm-notification-acceptance-gate.sh"
require_workflow_order "bash scripts/p5-rm-notification-acceptance-gate.sh" "Run core E2E gate"
require_workflow_order "Run RM notification acceptance gate" "Run core E2E gate"

echo "p5_rm_notification_closeout_preflight: check gate script syntax"
bash -n scripts/p5-rm-notification-acceptance-gate.sh

echo "p5_rm_notification_closeout_preflight: scan for bare API response.ok assertions"
if rg -n 'expect\([^\n]*\.ok\(\)\)\.toBeTruthy\(\)|expect\([^\n]*\.ok\(\)\)\.toBe\(true\)' \
  ecm-frontend/e2e/rm-report-preset-schedule.spec.ts; then
  echo "p5_rm_notification_closeout_preflight: found bare response.ok assertions" >&2
  exit 1
fi

echo "p5_rm_notification_closeout_preflight: discover RM notification acceptance tests"
discovery_output="$(cd ecm-frontend && npm run e2e:rm-notification:acceptance -- --list)"
printf '%s\n' "${discovery_output}"
discovered_count="$(printf '%s\n' "${discovery_output}" \
  | grep -Ec 'rm-report-preset-schedule\.spec\.ts:[0-9]+:[0-9]+.*@rm-notification-acceptance' || true)"
if [[ "${discovered_count}" != "${EXPECTED_ACCEPTANCE_TEST_COUNT}" ]]; then
  echo "p5_rm_notification_closeout_preflight: expected ${EXPECTED_ACCEPTANCE_TEST_COUNT} acceptance tests, found ${discovered_count}" >&2
  exit 1
fi

echo "p5_rm_notification_closeout_preflight: run People preference service contract tests"
(
  cd ecm-frontend
  CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/services/peopleService.test.ts --forceExit
)

echo "p5_rm_notification_closeout_preflight: run RM notification preference rollback tests"
(
  cd ecm-frontend
  CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/pages/RecordsManagementPage.test.tsx \
    --testNamePattern "rolls back .*preset delivery notification preference toggle" --forceExit
)

echo "p5_rm_notification_closeout_preflight: ok"
