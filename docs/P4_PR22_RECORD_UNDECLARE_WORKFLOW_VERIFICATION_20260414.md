# P4 PR-22 Record Undeclare Workflow Verification

## Delivery Scope

`PR-22` is now implemented.

Delivered behavior:

- backend `POST /api/v1/nodes/{nodeId}/record/undeclare`
- admin-only undeclare with required reason
- legal-hold preflight block
- file-plan governance block
- checked-out / working-copy block
- RM audit events:
  - `RM_RECORD_UNDECLARED`
  - `RM_RECORD_UNDECLARE_BLOCKED`
- preview UI undeclare action
- RM admin page undeclare action
- front-end undeclare confirmation dialog

## Backend Validation

Targeted backend regression:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test \
  -Dtest=RecordsManagementServiceTest,RecordsManagementControllerTest,RecordsManagementControllerSecurityTest
```

Result:

- `Tests run: 28`
- `Failures: 0`
- `Errors: 0`

Full backend regression:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

Result:

- `Tests run: 1538`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 11`

## Frontend Validation

Targeted front-end regression:

```bash
cd ecm-frontend
CI=true npm test -- --runInBand --watch=false --runTestsByPath \
  src/services/recordsManagementService.test.ts \
  src/components/records/UndeclareRecordDialog.test.tsx \
  src/pages/RecordsManagementPage.test.tsx \
  src/components/preview/DocumentPreview.undeclare.test.tsx
```

Result:

- `Test Suites: 4 passed`
- `Tests: 16 passed`

Full front-end regression:

```bash
cd ecm-frontend
CI=true npm test -- --runInBand --watch=false
```

Result:

- `Test Suites: 69 passed`
- `Tests: 325 passed`

Production build:

```bash
cd ecm-frontend
npm run build
```

Result:

- build succeeded
- remaining warnings are pre-existing unused imports in:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`

## Static Validation

```bash
git diff --check
```

Result:

- passed

## Files Changed

Backend:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerSecurityTest.java`

Frontend:

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/components/records/UndeclareRecordDialog.tsx`
- `ecm-frontend/src/components/records/UndeclareRecordDialog.test.tsx`
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/components/preview/DocumentPreview.undeclare.test.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
