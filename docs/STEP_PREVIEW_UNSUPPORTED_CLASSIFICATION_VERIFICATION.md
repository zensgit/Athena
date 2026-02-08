# Step: Preview Unsupported Classification (Verification)

## Verification Scope
- Backend classifier behavior.
- Frontend unsupported fallback behavior.
- E2E behavior for search and advanced search preview status UI.

## Executed Checks

1. Backend unit test
- Command:
  - `cd ecm-core && mvn -Dtest=PreviewFailureClassifierTest test`
- Result:
  - `BUILD SUCCESS`
  - `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

2. Frontend unit test
- Command:
  - `cd ecm-frontend && npm test -- --watch=false --runInBand src/utils/previewStatusUtils.test.ts`
- Result:
  - `PASS src/utils/previewStatusUtils.test.ts`
  - `7 passed, 0 failed`

3. E2E regression (Playwright CLI)
- Command:
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-preview-status.spec.ts --workers=1`
- Final Result:
  - `3 passed`
  - `0 failed`

## Functional Outcomes Confirmed
- Unsupported preview failures render as `Preview unsupported`.
- Unsupported preview cards do not show:
  - `Preview failure reason` button.
  - `Retry preview` button.
- Advanced search:
  - Keeps `previewStatus=FAILED` filter behavior.
  - Unsupported preview cards still hide card-level retry actions.
- Frontend fallback remains correct even when backend has no `previewFailureCategory` in existing indexed records.

## Notes
- During local dev-server verification, a TypeScript issue in `src/index.tsx` caused webpack overlay and flaky E2E clicks; this was fixed and verified (`No issues found` after compile).
- E2E assertions were refined to avoid environment-dependent false negatives while preserving required behavior checks.
