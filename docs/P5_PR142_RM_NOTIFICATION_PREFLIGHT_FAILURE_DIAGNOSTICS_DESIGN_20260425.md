# P5 PR-142 RM Notification Preflight Failure Diagnostics Design

## Goal

Make the RM notification closeout preflight script fail with a clear, intentional message when Playwright acceptance discovery finds the wrong number of tests.

## Problem

`PR-141` counted discovered `@rm-notification-acceptance` tests with `grep -c` under `set -euo pipefail`. If discovery ever returned zero matching tests, `grep` could terminate the script before the custom `expected 4 acceptance tests` diagnostic ran.

That would make a regression harder to understand.

## Change

`scripts/p5-rm-notification-closeout-preflight.sh` now:

- defines `EXPECTED_ACCEPTANCE_TEST_COUNT`, default `4`
- prints the expected count at startup
- uses `grep -Ec ... || true` for discovery counting
- always reaches the explicit count comparison and custom error message

## Boundaries

- no runtime behavior changed
- no Playwright test selection changed
- no CI workflow changed
- no acceptance status promoted
