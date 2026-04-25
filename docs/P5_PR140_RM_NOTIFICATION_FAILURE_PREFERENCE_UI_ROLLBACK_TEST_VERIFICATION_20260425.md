# P5 PR-140 RM Notification Failure Preference UI Rollback Test Verification

## Targeted Page Tests

Command:

```bash
CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/pages/RecordsManagementPage.test.tsx --testNamePattern "rolls back .*preset delivery notification preference toggle" --forceExit
```

Result:

- passed
- `Test Suites: 1 passed, 1 total`
- `Tests: 2 passed, 79 skipped, 81 total`

## Static Diff Check

Command:

```bash
git diff --check
```

Result:

- passed

## Related Contract Test

Command:

```bash
CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/services/peopleService.test.ts --forceExit
```

Result:

- passed
- `Test Suites: 1 passed, 1 total`
- `Tests: 7 passed, 7 total`

## Acceptance Discovery Check

Command:

```bash
cd ecm-frontend && npm run e2e:rm-notification:acceptance -- --list
```

Result:

- discovered four `@rm-notification-acceptance` Playwright tests
- `Total: 4 tests in 1 file`
