# P4 PR-26C Record Category Rename-Move UI Verification

## Implementation Summary

`PR-26C` was implemented as a thin frontend RM admin slice.

Delivered behavior:

- RM frontend service now supports record-category rename and move
- RM admin page exposes explicit `Rename` and `Move` actions for non-root record categories
- rename and move use dedicated dialogs instead of overloading the description-only edit form
- move dialog filters out the current category subtree and current parent from candidate targets, then requires an explicit new-parent selection
- root category remains protected in the table
- file-plan rename / move UI remains intentionally deferred

## Files Changed

Frontend:

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/components/records/RenameRecordCategoryDialog.tsx`
- `ecm-frontend/src/components/records/MoveRecordCategoryDialog.tsx`
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
- `Tests: 24 passed`

Coverage added in this slice includes:

- record-category rename service contract
- record-category move service contract
- file-plan edit blocked-state coverage for name / parent fields
- RM page action wiring for category rename / move
- move dialog explicit-selection and filtered-target wiring

## Full Regression

Command:

```bash
cd ecm-frontend
CI=true npm test -- --watchAll=false
```

Result:

- `Test Suites: 69 passed, 69 total`
- `Tests: 335 passed, 335 total`

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

`PR-26C` is approved. The thin RM admin UI now safely exposes record-category rename / move on top of the hardened backend contract, while file-plan rename / move UI remains deferred.
