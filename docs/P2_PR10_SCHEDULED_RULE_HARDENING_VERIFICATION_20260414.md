# P2 PR-10 Scheduled Rule Hardening Verification

## Date
- 2026-04-14

## Status
- passed

## Targeted Backend Tests

```bash
./ecm-core/mvnw -B -Dstyle.color=never test \
  -Dtest=RuleEngineServiceValidationTest,ScheduledRuleRunnerTest,RuleControllerScheduledValidationTest
```

### Result
- `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`

### Covered
- scheduled create/update rejects missing or invalid cron expressions
- scheduled create/update rejects sub-minimum interval schedules
- scheduled create/update rejects `maxItemsPerRun < 1`
- non-scheduled rules do not retain scheduled-only fields
- cron preview endpoint returns invalid payload for too-frequent schedules

## Targeted Frontend Tests

```bash
cd ecm-frontend
CI=true npm test -- --watch=false --runInBand src/pages/RulesPage.test.tsx
```

### Result
- `Test Suites: 1 passed, 1 total`
- `Tests: 2 passed, 2 total`

### Covered
- scheduled rule save is blocked when batch size is invalid
- switching trigger away from `SCHEDULED` clears scheduled payload fields before save

## Full Backend Regression

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

### Result
- `Tests run: 1432, Failures: 0, Errors: 0, Skipped: 11`

## Full Frontend Regression

```bash
cd ecm-frontend
CI=true npm test -- --watch=false --runInBand
```

### Result
- `Test Suites: 59 passed, 59 total`
- `Tests: 300 passed, 300 total`

## Static Check

```bash
git diff --check
```

### Result
- passed

## Verified Behavior
- scheduled rules now have a single shared validation contract across preview, persistence, and runtime scheduling
- invalid minute-level schedules are rejected with a clear error instead of being silently accepted
- non-scheduled rules no longer persist stale scheduled configuration
- `RulesPage` submits normalized scheduled payloads and drops scheduled fields when the trigger changes
- manual trigger permission semantics remain unchanged and continue to be covered by existing controller security tests
