# P5 PR89 RM Report Preset Save-As-Preset UI Verification

## Scope Verified

- the RM page now exposes `Save current preset` / `Save previous preset` on the targeted report cards
- the new dialog validates preset naming and posts through the existing preset API
- save actions preserve the current/previous window semantics already used by export and audit drilldown
- no backend API, migration, or runtime policy contract changed

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
  --testNamePattern="preset|activity family report CSVs|activity contributor report CSVs" \
  --forceExit
```

Result:

- `Test Suites: 1 passed, 1 total`
- `Tests: 4 passed, 61 skipped, 65 total`

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

- service-level preset creation payload trimming
- contributor-report preset creation for the previous rolling window
- activity-family-report preset creation for the current named window
- existing CSV export coverage for the same cards remains intact

## Residual Manual Check

Still recommended in staging:

- open the RM page
- save a preset from one current-window card and one previous-window card
- verify the saved preset name, description, kind, and `from/to` window match the UI expectation

## Residual Limits

- this slice only adds preset creation from the RM page; it does not add preset list/edit/delete/execute UI
- the backend preset API remains admin-only
- current targeted tests do not separately cover cancel, backend failure, or authorization failure flows
- highlight/mix cards intentionally save report/export-oriented preset kinds rather than a separate visualization-only preset contract
