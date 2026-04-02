# Phase 247 - Ops Recovery Dry-run Filter Modes (Verification)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Backend Verification

### 1.1 Controller security + behavior

Command:

```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```

Result: PASS

Validated:

- `dry-run` accepts `mode=CLEAR_BY_FILTER` and returns successful estimates.
- Dry-run sample includes `predictedState=CLEARED` / `predictedOutcome=CLEARED` for matched dead-letter entries.
- No unintended queue side effect on dry-run path (`previewQueueService.enqueue` not called).

## 2. Frontend Static Gates

### 2.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/opsRecoveryService.ts
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
npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result: PASS

Validated:

- Ops Recovery Dry-run panel paths for `QUEUE_BY_WINDOW`, `CLEAR_BY_FILTER`, and `REPLAY_BY_FILTER` all execute in mocked flow.
- New dry-run mode calls are captured (`mode=CLEAR_BY_FILTER`, `mode=REPLAY_BY_FILTER`).
- Preview diagnostics mocked scenario remains green after dry-run mode expansion.
- Replay toast selector ambiguity is resolved (`/^Replay queued:/i`), avoiding strict-mode collision with dead-letter replay toast.

## 4. Acceptance Checklist

- [x] Backend dry-run supports dead-letter filter modes (`CLEAR_BY_FILTER`, `REPLAY_BY_FILTER`)
- [x] Frontend dry-run panel can execute filter-governed clear/replay flows
- [x] Static + behavior gates pass (backend test, lint, build, mocked e2e)
