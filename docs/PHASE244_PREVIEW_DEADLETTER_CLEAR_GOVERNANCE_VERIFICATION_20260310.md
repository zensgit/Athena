# Phase 244 - Preview Dead-letter Clear Governance API + Diagnostics UI (Verification)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Backend Verification

### 1.1 Controller security + behavior test

Command:

```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest test
```

Result: PASS

Validated:

- new endpoint `/preview/diagnostics/dead-letter/clear-batch` is admin-gated
- admin clear-batch flow returns `CLEARED`
- audit event `PREVIEW_DEAD_LETTER_CLEARED` is emitted

## 2. Frontend Static Verification

### 2.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts
```

Result: PASS

### 2.2 Build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result: PASS

## 3. Mocked E2E Verification

Command:

```bash
cd ecm-frontend
npx serve -s build -l 5500
# another shell
npx playwright test admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result: PASS

Validated:

- `Clear Selected` issues dead-letter clear-batch request
- per-row `Clear dead-letter ...` action works
- replay/export flows remain functional in same diagnostics scenario
- clear-batch call assertions are satisfied in test summary

## 4. Acceptance Checklist

- [x] Backend provides dead-letter clear-batch API with per-entry outcomes
- [x] API is protected by admin role
- [x] Diagnostics UI supports batch/single clear operations
- [x] Mocked e2e covers clear + replay + export regression path
