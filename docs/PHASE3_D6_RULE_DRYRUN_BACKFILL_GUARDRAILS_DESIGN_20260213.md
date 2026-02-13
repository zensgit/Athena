# Phase 3 Day 6 Design: Rule Dry-Run and Manual Backfill Guardrails

Date: 2026-02-13

## Goal

Finalize Day 6 of Phase 3 by validating and documenting guardrails around:

1. mail diagnostics dry-run flows
2. scheduled rule manual trigger behavior with backfill windows
3. validation limits for manual backfill configuration

## Existing Functional Shape

The current implementation already exposes the required behavior:

1. Mail dry-run diagnostics and rule preview endpoints in `MailAutomationController`
2. Scheduled rule manual trigger logic in `ScheduledRuleRunner`
3. Validation guardrails in rule validation paths (`RuleEngineService` + `RulesPage` form checks)

## Guardrail Contract

1. Scheduled rules must reject invalid manual-backfill values.
2. Manual trigger should compute an effective `since` window:
   - bounded by configured/override backfill minutes
   - not older than intended unless last run is older
3. Dry-run paths must not mutate persistent document state.
4. Security checks must still apply to manual trigger and diagnostics actions.

## Verification Strategy

Use targeted backend tests that directly cover the contract:

- `ScheduledRuleRunnerTest`
- `RuleEngineServiceValidationTest`
- `MailAutomationControllerDiagnosticsTest`
- `MailAutomationControllerSecurityTest`

Expected outcome:

- all tests pass
- logs show expected scheduled-rule trigger and backfill calculations

