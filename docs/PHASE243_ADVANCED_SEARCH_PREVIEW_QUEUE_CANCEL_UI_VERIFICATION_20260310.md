# Phase 243 - Advanced Search Preview Queue Cancel UI + Governance Feedback (Verification)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Frontend Static Gates

## 1.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts
```

Result: PASS

## 1.2 Production build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result: PASS

## 2. Mocked E2E Verification

## 2.1 Scenario: retry then cancel preview queue task

Command:

```bash
cd ecm-frontend
npx serve -s build -l 5500
# (in another shell)
npx playwright test e2e/advanced-search-preview-batch-scope.mock.spec.ts -g "retry all matched failed previews scans across pages"
```

Result: PASS

Validated:

- result-level `Retry failed previews` still queues preview task.
- `Cancel preview task` button appears when queue state is active.
- cancel endpoint is called once per cancel operation.
- UI reflects terminal queue state (`Queue state: CANCELLED`) after response.

## 3. Acceptance Checklist

- [x] Advanced Search UI can cancel active preview queue task.
- [x] Queue-state/message surfaced in result diagnostics.
- [x] Retry/cancel action gating avoids invalid idle operations.
- [x] Mocked e2e regression updated and passing.
