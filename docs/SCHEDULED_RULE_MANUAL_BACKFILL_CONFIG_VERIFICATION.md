# Scheduled Rule Manual Backfill Config Verification

## Backend Tests
- Full backend suite:
  - Command: `cd ecm-core && mvn -q test`
  - Result: ✅ Passed
- Targeted manual trigger test:
  - Command: `cd ecm-core && mvn -q -Dtest=ScheduledRuleRunnerTest test`
  - Result: ✅ Passed
  - Includes: fallback to default backfill when `manualBackfillMinutes <= 0`
- Targeted validation test:
  - Command: `cd ecm-core && mvn -q -Dtest=RuleEngineServiceValidationTest test`
  - Result: ✅ Passed (rejects out-of-range values; accepts valid values on create + update)

## Migration
- Rebuilt backend container:
  - Command: `docker-compose up -d --build ecm-core`
- Liquibase applied the new column:
  - Observed in logs: change set `019-1-add-manual-backfill-minutes` ran successfully.

## Playwright E2E
- Scheduled rule flow (includes manual backfill UI assertion):
  - Command: `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "Scheduled Rules"`
  - Result: ✅ Passed
- Manual backfill validation guard (out-of-range blocks POST):
  - Command: `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/rules-manual-backfill-validation.spec.ts`
  - Result: ✅ Passed
- Scheduled rules stress runner:
  - Command: `cd ecm-frontend && npm run test:scheduled:stress`
  - Result: ✅ Passed (5/5)
- Full regression:
  - Command: `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Result: ✅ 22 passed (~5.5m)

## Observability
- Backend logs now include the effective manual trigger window:
  - Example: `Manual trigger for scheduled rule '...' uses since=... (lastRunAt=..., backfillMinutes=...)`
- The Trigger cell now includes backfill details, and E2E assertions were updated to match `SCHEDULED` non-exactly.

## Conclusion
- The manual backfill window is now configurable per scheduled rule.
- The manual trigger race fix remains stable under full E2E regression.
