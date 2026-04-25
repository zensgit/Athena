# P5 PR-137 RM Notification Preference Service Contract Tests Verification

## Service Unit Test

Command:

```bash
CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/services/peopleService.test.ts
```

Result:

- passed
- `Test Suites: 1 passed, 1 total`
- `Tests: 7 passed, 7 total`

## Static Diff Check

Command:

```bash
git diff --check
```

Result:

- passed
