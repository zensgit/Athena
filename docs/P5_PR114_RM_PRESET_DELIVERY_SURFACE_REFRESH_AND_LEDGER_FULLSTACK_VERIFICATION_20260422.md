# P5 PR-114 RM Preset Delivery Surface Refresh And Ledger Full-Stack Verification

## Scope Verified

- page-level preset/health/ledger surfaces refresh after successful schedule save and manual delivery
- summary-only preset full-stack delivery now proves:
  - `Export CSV`
  - `Schedule Delivery`
  - `Deliver now`
  - delivered file exists in target folder
  - delivered execution appears in page-level `Preset Delivery Ledger`

## Unit Verification

### Targeted frontend tests

```bash
cd ecm-frontend
CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/components/records/ScheduleReportPresetDialog.test.tsx src/pages/RecordsManagementPage.test.tsx --forceExit
```

Result:

```text
Test Suites: 2 passed, 2 total
Tests: 83 passed, 83 total
```

### Frontend build

```bash
cd ecm-frontend
npm run build
```

Result:

- passed

## Full-Stack Verification

### Backend health

```bash
curl -s http://127.0.0.1:7700/actuator/health
```

Result:

```text
{"status":"UP"}
```

### Full-stack Playwright

```bash
cd ecm-frontend
ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --workers=1
```

Result:

```text
Running 2 tests using 1 worker
  ✓ RM report preset schedule can be configured from Records Management (full-stack)
  ✓ RM summary-only preset can be exported and scheduled from Records Management (full-stack)
  2 passed
```

## Static Check

```bash
git diff --check
```

Result:

- passed

## Conclusion

`PR-114` is accepted as frontend-only operator polish on top of the shipped preset scheduled-delivery chain. The summary-only preset flow now has both dialog-level and page-level full-stack evidence.
