# P5 PR-144B RM Notification Acceptance Scenario Contract Design

## Goal

Make the RM notification closeout preflight prove that the four required acceptance scenarios are still present, not only that four tagged Playwright tests exist.

## Problem

`PR-142` and `PR-144` guarded the acceptance test count. That catches missing or renamed tagged tests, but it does not catch a false-positive replacement where the suite still has four `@rm-notification-acceptance` tests while one of the required product scenarios disappears.

The lane needs four specific flows:

- failed scheduled delivery creates an inbox notification
- successful scheduled delivery creates an inbox notification
- disabled success notification preference suppresses the inbox alert
- disabled failure notification preference suppresses the inbox alert

## Change

`scripts/p5-rm-notification-closeout-preflight.sh` now defines the expected scenario titles and checks them twice:

- static check against `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`
- discovery-output check after `npm run e2e:rm-notification:acceptance -- --list`

The static check accepts `ACCEPTANCE_SPEC_FILE`, which allows deterministic negative tests against a temporary mutated spec without editing the working tree.

## Boundaries

- this does not run the full browser acceptance flows
- this does not replace the GitHub Actions live acceptance gate
- this does not change runtime behavior, endpoints, schema, or UI
- this keeps `PR-145` reserved for email delivery after P0 acceptance
