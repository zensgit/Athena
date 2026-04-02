# Phase 238 - Search Preview Skip Diagnostics (Verification)

Date: 2026-03-09

## 1. Backend verification

Command:

```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest test
```

Result:

- PASS
- Verified `skipBreakdown` in:
  - `POST /api/v1/search/preview/queue-failed`
  - `POST /api/v1/search/preview/queue-failed/dry-run`
- Verified dry-run CSV includes:
  - `skipReason,count`

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

## 4. Mock e2e gate

Command:

```bash
bash scripts/phase235-preview-async-task-center-mock-e2e.sh
```

Result:

- PASS (`3 passed`)
  - `admin-preview-diagnostics.mock.spec.ts`
  - `advanced-search-preview-batch-scope.mock.spec.ts` (2 tests)

## 5. Behavioral coverage

1. Dry-run and queue search-scope responses carry `skipBreakdown`.
2. Advanced Search UI renders `Skipped diagnostics` chips.
3. Dry-run/export paths keep reason + skip diagnostics aligned in CSV and UI.
