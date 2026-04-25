#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

EXPECTED_ACCEPTANCE_TEST_COUNT="${EXPECTED_ACCEPTANCE_TEST_COUNT:-4}"

echo "p5_rm_notification_closeout_preflight: start"
echo "EXPECTED_ACCEPTANCE_TEST_COUNT=${EXPECTED_ACCEPTANCE_TEST_COUNT}"

echo "p5_rm_notification_closeout_preflight: check workflow yaml"
ruby -e 'require "yaml"; YAML.load_file(".github/workflows/ci.yml"); puts "yaml ok"'

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
