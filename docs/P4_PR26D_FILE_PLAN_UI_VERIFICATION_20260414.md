# P4 PR-26D File Plan Rename-Move UI Verification

## Implementation Summary

`PR-26D` was implemented as a thin frontend RM admin slice.

Delivered behavior:

- RM frontend service now supports file-plan rename and move
- RM admin page exposes explicit `Rename` and `Move` actions for file plans
- rename and move use dedicated dialogs instead of overloading the description-only edit form
- move dialog filters out the current file plan, descendant file plans, and the current parent when that parent is another file plan
- move dialog requires explicit selection of a new file-plan parent before submit
- workspace/system-root targets remain intentionally deferred in this thin UI

## Files Changed

Frontend:

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/components/records/RenameFilePlanDialog.tsx`
- `ecm-frontend/src/components/records/MoveFilePlanDialog.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
- `ecm-frontend/src/services/recordsManagementService.test.ts`

## Targeted Validation

Command:

```bash
cd ecm-frontend
CI=true npm test -- --watchAll=false --runInBand src/pages/RecordsManagementPage.test.tsx src/services/recordsManagementService.test.ts
```

Result:

- `Test Suites: 2 passed`
- `Tests: 28 passed`

Coverage added in this slice includes:

- file-plan rename service contract
- file-plan move service contract
- file-plan rename dialog happy path
- file-plan move wiring with filtered candidate targets
- file-plan description-only edit blocked-state coverage

## Full Regression

Command:

```bash
cd ecm-frontend
CI=true npm test -- --watchAll=false
```

Result:

- `Test Suites: 69 passed, 69 total`
- `Tests: 339 passed, 339 total`

Build command:

```bash
cd ecm-frontend
npm run build
```

Build result:

- `Compiled with warnings`
- production build completed successfully
- remaining warnings are pre-existing unused imports in:
  - `ecm-frontend/src/components/share/ShareLinkManager.tsx`
  - `ecm-frontend/src/pages/AdminDashboard.tsx`

## Static Checks

Command:

```bash
git diff --check
```

Result:

- passed

## Verification Conclusion

`PR-26D` is approved. The thin RM admin UI now safely exposes file-plan rename / move on top of the hardened backend contract, while workspace/system-root targets remain intentionally deferred.
