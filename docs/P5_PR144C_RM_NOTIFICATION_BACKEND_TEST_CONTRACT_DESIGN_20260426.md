# P5 PR-144C RM Notification Backend Test Contract Design

## Goal

Make the RM notification closeout preflight prove that the live acceptance gate still targets the five backend test classes required for the notification lane.

## Problem

The live gate runs backend targeted tests before readiness checks and Playwright acceptance. If the `BACKEND_TESTS` default in `scripts/p5-rm-notification-acceptance-gate.sh` drifts, CI may still run but no longer cover part of the notification lane.

The required backend test classes are:

- `RmReportPresetDeliveryServiceTest`
- `RmReportPresetControllerTest`
- `RmReportPresetControllerSecurityTest`
- `ActivityServiceTest`
- `NotificationInboxServiceTest`

## Change

`scripts/p5-rm-notification-closeout-preflight.sh` now checks the backend test contract before frontend discovery and unit tests:

- each required class name appears in the acceptance gate script
- each required Java test class exists under `ecm-core/src/test/java`

The script accepts `ACCEPTANCE_GATE_SCRIPT` and `BACKEND_TEST_SOURCE_ROOT`, so negative checks can run against temporary mutated files without editing the working tree.

## Boundaries

- this does not run Maven tests locally
- this does not replace the live GitHub Actions acceptance gate
- this does not change runtime behavior, database schema, endpoints, or UI
- this keeps `PR-145` reserved for the email delivery lane after P0 acceptance
