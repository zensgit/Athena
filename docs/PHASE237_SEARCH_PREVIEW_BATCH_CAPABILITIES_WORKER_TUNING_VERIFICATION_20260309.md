# Phase 237 - Search Preview Batch Capabilities + Worker Tuning (Verification)

Date: 2026-03-09

## 1. Backend verification

Command:

```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest test
```

Result:

- PASS
- Security and controller tests include new capability endpoint and worker-count assertions.

## 2. Frontend static checks

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts e2e/advanced-search-preview-batch-scope.mock.spec.ts
```

Result:

- PASS

## 3. Frontend production build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result:

- PASS
- Production bundle generated successfully.

## 4. Mocked e2e gate

Command:

```bash
bash scripts/phase235-preview-async-task-center-mock-e2e.sh
```

Result:

- PASS (`3 passed`)
  - `admin-preview-diagnostics.mock.spec.ts`
  - `advanced-search-preview-batch-scope.mock.spec.ts` (2 tests)

## 5. Behavioral checks covered

1. Admin capability endpoint availability and non-admin denial.
2. Dry-run and queue batch payload carry configurable `workerCount`.
3. UI worker selector drives request payload.
4. Dry-run/export artifacts include worker governance metadata.

