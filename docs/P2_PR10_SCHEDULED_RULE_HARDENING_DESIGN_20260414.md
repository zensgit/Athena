# P2 PR-10 Scheduled Rule Hardening Design

## Date
- 2026-04-14

## Goal
- harden Athena's existing scheduled-rule path without rebuilding the runner or widening the permission model

## Scope
- backend scheduled-rule validation and normalization
- frontend scheduled-rule authoring state
- targeted backend/controller/frontend coverage

## Why This Shape
- Athena already had:
  - `ScheduledRuleRunner`
  - cron preview endpoint
  - scheduled fields on `AutomationRule`
  - `RulesPage` authoring UI
- the real gap was not missing infrastructure
- the real gap was inconsistent schedule authoring semantics:
  - non-scheduled rules still persisted scheduled-only fields
  - cron validity was preview-only rather than enforced on save
  - there was no explicit minimum interval floor
  - the UI retained stale scheduled state when trigger type changed

## Design

### 1. Shared Scheduled Validation Utility
- add `ScheduledRuleValidation`
- responsibilities:
  - normalize cron expression and timezone
  - validate timezone identifiers
  - enforce `maxItemsPerRun >= 1`
  - enforce minimum schedule interval of `5` minutes
  - compute next run timestamps
  - provide next-execution previews for the cron validation API
- this removes duplicated schedule semantics between `RuleEngineService` and `ScheduledRuleRunner`

### 2. Rule Create/Update Normalization
- `RuleEngineService.createRule(...)` now:
  - rejects missing trigger type
  - validates scheduled-rule shape at write time
  - computes `nextRunAt` for valid scheduled rules
  - clears all scheduled-only fields for non-scheduled rules
- `RuleEngineService.updateRule(...)` now:
  - resolves the effective trigger type first
  - validates the effective scheduled configuration after partial updates
  - recomputes `nextRunAt` for scheduled rules
  - clears `cronExpression/timezone/maxItemsPerRun/manualBackfillMinutes/nextRunAt/lastRunAt` when a rule switches away from `SCHEDULED`

### 3. Runner and Preview Endpoint Alignment
- `ScheduledRuleRunner.validateCronExpression(...)` now reuses the same shared validator used by rule persistence
- `ScheduledRuleRunner.updateNextRunTime(...)` now recomputes `nextRunAt` with the shared validator instead of parsing cron independently
- effect:
  - preview and persistence reject the same invalid schedule shapes
  - runtime no longer drifts from authoring semantics

### 4. RulesPage State Hardening
- `RulesPage` now keeps scheduled authoring state explicit:
  - switching trigger away from `SCHEDULED` resets scheduled-only fields to defaults
  - switching into `SCHEDULED` restores safe defaults for timezone and batch size
  - save path validates `maxItemsPerRun >= 1`
  - cron helper text now documents the `5` minute minimum interval
  - scheduled payload is trimmed/normalized before `createRule/updateRule`
- this keeps the frontend payload aligned with backend normalization instead of relying on backend cleanup alone

### 5. Permission Boundary
- `PR-10` does not widen manual trigger permissions
- existing controller security remains authoritative:
  - scheduled manual trigger stays admin-gated
  - folder scope visibility and authoring boundaries remain enforced by existing rule visibility/scope logic
- the hardening in this phase is correctness-oriented, not a product-policy expansion

## Primary Files
- [ScheduledRuleValidation.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ScheduledRuleValidation.java:1)
- [RuleEngineService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java:101)
- [ScheduledRuleRunner.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ScheduledRuleRunner.java:1)
- [RulesPage.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/pages/RulesPage.tsx:1)
- [RuleEngineServiceValidationTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RuleEngineServiceValidationTest.java:1)
- [ScheduledRuleRunnerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/ScheduledRuleRunnerTest.java:1)
- [RuleControllerScheduledValidationTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RuleControllerScheduledValidationTest.java:1)
- [RulesPage.test.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/pages/RulesPage.test.tsx:1)

## Non-goals
- no new scheduled-rule tables or migrations
- no new scheduler infrastructure
- no change to manual trigger role policy
- no redesign of folder-scope rule management UI
