# Phase 240 - Preview Dead-Letter Rendition Governance + Content-Hash Binding (Verification)

Date: 2026-03-10  
Scope: `ecm-core` + `ecm-frontend`

## 1. Backend Verification

## 1.1 Compile

Command:

```bash
cd ecm-core
mvn -DskipTests compile
```

Result: PASS

## 1.2 Controller + queue + search regression set

Command:

```bash
cd ecm-core
mvn -q -Dtest=PreviewDeadLetterRegistryTest,PreviewQueueServiceTest,PreviewDiagnosticsControllerSecurityTest,OpsRecoveryControllerSecurityTest,SearchControllerTest,SearchControllerSecurityTest test
```

Result: PASS

Coverage from this run includes:

- Dead-letter replay and export controller contracts
- Ops recovery replay-batch with entry-key aware backend path
- Queue dead-letter recording and auto-replay behavior
- Search preview batch APIs regression (existing Phase 239 coverage)

## 1.3 Redis/Testcontainers backend tests

Attempted:

```bash
cd ecm-core
mvn -q -Dtest=PreviewDeadLetterRegistryRedisBackendTest,PreviewQueueServiceRedisBackendTest test
```

Status: BLOCKED in current environment due Docker daemon connectivity/pull hang (Testcontainers bootstrap did not complete).  
Action: mark as environment-dependent pending run on a Docker-healthy CI or dev host.

## 2. Frontend Verification

## 2.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts src/services/opsRecoveryService.ts e2e/admin-preview-diagnostics.mock.spec.ts
```

Result: PASS

## 2.2 Production build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result: PASS

## 2.3 Mock e2e

Command:

```bash
cd ecm-frontend
npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

Result: BLOCKED in current environment (`http://localhost:5500` unavailable, `ERR_CONNECTION_REFUSED`).  
Code-side compatibility for `entryKeys` request payload is implemented in the mock route layer.

## 3. Acceptance Checklist

- [x] Dead-letter ledger is tuple-keyed by `(documentId, renditionKey)`.
- [x] Replay API supports tuple-level targeting via `entryKeys`.
- [x] Queue dedup governance key implemented (`document+rendition+contentHash`).
- [x] READY skip logic is content-hash-bound (no blind READY trust).
- [x] `preview_content_hash` persisted and maintained by preview lifecycle.
- [x] Backend compile + core regression tests passing.
- [x] Frontend lint + build passing.
- [ ] Redis/Testcontainers tests pending Docker-ready environment.
- [ ] Playwright mock e2e pending running UI server.
