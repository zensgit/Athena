# P5 PR112 RM Summary Preset Schedule Mocked E2E Verification

## Scope Verified

Verified mocked browser-level coverage for summary-only preset kinds:

- `ACTIVITY_FAMILY_HIGHLIGHTS`
- `ACTIVITY_FAMILY_MIX`

The spec now proves:

- summary-only preset rows show `Export CSV`
- summary-only preset rows show `Schedule`
- summary-only preset export hits the family-report CSV route
- summary-only preset schedule dialog loads using the current folder-picker UI
- summary-only preset can save schedule state
- summary-only preset can run `Deliver now`

## Playwright Verification

Command:

```bash
cd ecm-frontend && ECM_UI_URL=http://localhost:3000 npx playwright test e2e/rm-report-preset-schedule.mock.spec.ts --workers=1
```

Result:

- `1 passed`

Executed against:

- current working-tree frontend dev server on `http://localhost:3000`
- mocked `/api/v1/**` responses inside the Playwright spec

## Static Verification

Command:

```bash
git diff --check
```

Result:

- passed

## Notes

- this slice intentionally stays mocked-only; it does not expand the shipped full-stack/admin smoke
- no backend or frontend runtime behavior changed in this slice
- this closes the stale browser-level contract that still treated summary-only presets as audit-only after `PR-111`
