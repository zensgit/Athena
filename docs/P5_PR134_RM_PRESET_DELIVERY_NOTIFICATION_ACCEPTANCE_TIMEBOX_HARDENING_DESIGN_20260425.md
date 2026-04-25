# P5 PR-134 RM Preset Delivery Notification Acceptance Timebox Hardening Design

## Goal

Reduce CI runtime and clock-boundary flake in the four RM notification acceptance Playwright flows.

## Problem

The acceptance tests scheduled presets with a near-future cron expression and then waited until the backend-calculated `nextRunAt` became due. Each of the four flows allowed up to 150 seconds, so the CI gate could spend several minutes waiting on time rather than validating notification behavior.

## Change

The four `@rm-notification-acceptance` tests now:

- use one stable valid hourly cron expression
- call `forcePresetNextRunAtPast(preset.id)` immediately after schedule save
- keep a short API poll to verify the forced due state is visible
- preserve the existing `/run-scheduled-deliveries` trigger and notification/preference assertions

## Rationale

These tests are not trying to validate cron parsing. They validate:

- due scheduled delivery execution
- success and failure owner inbox notifications
- success and failure preference suppression
- notification drilldown behavior

Forcing `next_run_at` into the past keeps those semantics intact while removing unnecessary real-time waiting.

## Boundaries

- no backend runtime behavior changed
- no frontend runtime behavior changed
- no acceptance cases were removed
- no CI workflow topology changed
