# P5 PR90 RM Report Preset List and Apply UI Verification

## Scope Verified

- the RM page now lists saved report presets from the existing preset API
- saved presets can be applied to the existing `Records Audit` table
- saved presets can reuse the existing CSV export routes
- no backend API, migration, or policy behavior changed

## Commands

### Frontend targeted tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath \
  src/services/recordsManagementService.test.ts \
  --forceExit
```

Result:

- `Test Suites: 1 passed, 1 total`
- `Tests: 35 passed, 35 total`

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath \
  src/pages/RecordsManagementPage.test.tsx \
  --testNamePattern="preset|activity family report CSVs|activity contributor report CSVs|saved RM report preset" \
  --forceExit
```

Result:

- `Test Suites: 1 passed, 1 total`
- `Tests: 6 passed, 61 skipped, 67 total`

### Frontend build

```bash
cd ecm-frontend && npm run build
```

Result:

- passed
- remaining warnings are pre-existing:
  - `ShareLinkManager.tsx`
  - `AdminDashboard.tsx`

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Coverage Notes

Targeted regression coverage now includes:

- service-level preset listing through `GET /records/report-presets`
- page-level apply-to-audit behavior for a saved preset
- page-level export reuse for a saved preset
- previously shipped save-as-preset coverage remains intact

## Residual Limits

- this slice does not add preset edit/delete UI
- this slice does not add a generic preset execution endpoint
- preset export remains limited to known report kinds that already map to existing CSV routes
