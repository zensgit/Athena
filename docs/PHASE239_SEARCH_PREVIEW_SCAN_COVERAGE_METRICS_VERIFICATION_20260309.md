# Phase 239 - Search Preview Scan Coverage Metrics (Verification)

Date: 2026-03-09

## 1. Backend tests

Command:

```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest test
```

Result:

- PASS
- Verified:
  - queue/dry-run responses return `scanSkipped`
  - sync/async dry-run CSV includes `"scanSkipped","..."`

## 2. Frontend lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts e2e/advanced-search-preview-batch-scope.mock.spec.ts
```

Result:

- PASS

## 3. Frontend build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result:

- PASS

## 4. Mocked e2e gate

Command:

```bash
bash scripts/phase235-preview-async-task-center-mock-e2e.sh
```

Result:

- PASS (`3 passed`)
  - `admin-preview-diagnostics.mock.spec.ts`
  - `advanced-search-preview-batch-scope.mock.spec.ts` (2 tests)

## 5. Validated behavior

1. Search-scope queue/dry-run contracts expose skipped-scan total.
2. Dry-run summary UI displays `scanned` + `skipped` + `workers`.
3. Export and runtime diagnostics remain consistent.
