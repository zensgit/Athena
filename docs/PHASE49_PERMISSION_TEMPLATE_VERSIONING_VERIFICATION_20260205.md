# Phase 49 - Permission Template Versioning (Verification)

## Automated Checks
- Backend: `mvn test`
- Frontend E2E: `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/permission-templates.spec.ts`

## Results (2026-02-05)
- Backend tests: ✅ `mvn test`
- E2E (permission templates): ✅ 2 passed

## Notes
- E2E run against dev server on port 3000 (react-scripts) and API on 7700.

## Full E2E Suite (2026-02-05)
- Command: `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`
- Result: ✅ 36 passed

## Manual Smoke Steps
1. Log in as admin.
2. Go to **Admin → Permission Templates**.
3. Create or edit a template.
4. Click the history icon for the template.
5. Confirm version rows are shown and that **Restore** rolls back the template.

## Expected Results
- Version list shows at least one snapshot after create, and new snapshots after edits.
- Restore updates the template and appends a new version snapshot.
