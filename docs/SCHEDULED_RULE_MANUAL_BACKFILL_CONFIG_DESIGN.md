# Scheduled Rule Manual Backfill Config Design

## Goal
- Make the manual trigger backfill window configurable from the Rules UI without breaking existing scheduled rule behavior.

## Approach
- Add a per-rule override:
  - New column: `automation_rules.manual_backfill_minutes`
  - New API field: `manualBackfillMinutes`
- Guardrails:
  - Service validation enforces `manualBackfillMinutes` within `1-1440` when provided.
- Manual trigger behavior:
  - Use `rule.manualBackfillMinutes` when set and positive
  - Otherwise fall back to `ecm.rules.scheduled.manual-backfill-minutes`
- Keep the scheduler path unchanged.

## Backend Changes
- Entity:
  - `ecm-core/src/main/java/com/ecm/core/entity/AutomationRule.java`
- Runner:
  - `ecm-core/src/main/java/com/ecm/core/service/ScheduledRuleRunner.java`
- API wiring:
  - `ecm-core/src/main/java/com/ecm/core/controller/RuleController.java`
  - `ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java`
    - Range validation is applied in the service layer.
- Migration:
  - `ecm-core/src/main/resources/db/changelog/changes/019-add-scheduled-rule-manual-backfill-minutes.xml`
  - `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`

## Frontend Changes
- Types:
  - `ecm-frontend/src/services/ruleService.ts`
- UI:
  - `ecm-frontend/src/pages/RulesPage.tsx`
    - Scheduled rules now show the effective backfill setting in the Trigger cell.
    - Save validation now guards `manualBackfillMinutes` to `1-1440` to match backend rules.

## Test Coverage
- Unit test for manual trigger selection logic:
  - `ecm-core/src/test/java/com/ecm/core/service/ScheduledRuleRunnerTest.java`
- E2E scheduled rule flow now asserts UI and API round-trip:
  - `ecm-frontend/e2e/ui-smoke.spec.ts`
- A lightweight stress runner helps catch regressions quickly:
  - `ecm-frontend/scripts/scheduled-rules-stress.sh`
  - `ecm-frontend/package.json` exposes `npm run test:scheduled:stress`
