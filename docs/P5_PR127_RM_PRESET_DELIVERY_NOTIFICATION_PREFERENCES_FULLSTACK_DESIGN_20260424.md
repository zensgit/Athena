# P5 PR-127 RM Preset Delivery Notification Preferences Full-Stack Design

## Goal

Prove that the `PR-126` RM preset delivery notification preferences suppress real inbox rows in the current full-stack environment.

## Scope

This is a test-only slice.

- extend the existing RM preset scheduled-delivery Playwright spec
- set owner preferences through the existing People preferences API
- execute due scheduled deliveries through the existing admin trigger
- verify execution still happens while `/notifications` does not show the muted preset alert

## Covered Preferences

- `org.athena.rm.reportPreset.delivery.notifyOnSuccess=false`
- `org.athena.rm.reportPreset.delivery.notifyOnFailure=false`

## Design

The spec reuses the shipped full-stack delivery helpers:

- create a real report preset
- configure a scheduled delivery
- use a short UTC cron expression that naturally becomes due within the test window
- invoke `POST /api/v1/records/report-presets/run-scheduled-deliveries`
- verify the execution status reaches `SUCCESS` or `FAILED`
- verify unread notification count does not increase
- verify the `/notifications` UI does not contain the current preset name

The negative assertion combines the unique preset name with unread-count stability. The preset name check keeps the assertion scoped to the test-created delivery; unread-count stability catches accidental direct inbox writes for the same owner.

The new disabled-preference tests intentionally avoid the PostgreSQL `next_run_at` helper. They prove the same public API path an operator can exercise without requiring a Docker socket from the Playwright process.

## Non-Goals

- no runtime code change
- no new API
- no migration
- no email notification channel
- no generic preference-management UI

## Files

- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`
- `docs/P5_PR127_RM_PRESET_DELIVERY_NOTIFICATION_PREFERENCES_FULLSTACK_DESIGN_20260424.md`
- `docs/P5_PR127_RM_PRESET_DELIVERY_NOTIFICATION_PREFERENCES_FULLSTACK_VERIFICATION_20260424.md`
