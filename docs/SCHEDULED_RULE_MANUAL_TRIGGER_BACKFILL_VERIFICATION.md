# Scheduled Rule Manual Trigger Backfill Verification

## Scope
- Verify scheduled rule manual trigger is stable even when the scheduler runs close to document upload time.

## Backend Tests
- Targeted unit test:
  - Command: `cd ecm-core && mvn -q -Dtest=ScheduledRuleRunnerTest test`
  - Result: ✅ Passed
- Full backend test suite:
  - Command: `cd ecm-core && mvn -q test`
  - Result: ✅ Passed

## Service Rebuild
- Rebuilt backend container:
  - Command: `docker-compose up -d --build ecm-core`
  - Result: ✅ Backend started successfully

## Playwright Verification
- Scheduled rules test only:
  - Command: `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "Scheduled Rules"`
  - Result: ✅ Passed (`Tag 'scheduled-e2e-tag' found on document after 1 attempt(s)`)
- Full regression suite:
  - Command: `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Result: ✅ 21 passed (~5.9m)

## Conclusion
- The manual trigger race is addressed with a small backfill window.
- The previously failing scheduled rule regression now passes consistently in this environment.
