# Phase 169 - Ops Recovery Control Plane (Verification)

## Date
2026-03-06

## Scope
- Verify new `/api/v1/ops/recovery` admin control-plane endpoints.
- Ensure preview diagnostics baseline compatibility is not regressed.
- Validate frontend service contract compiles and passes lint/build checks.

## Commands and results

1. Backend new controller test
```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```
- Result: PASS

2. Backend compatibility check with existing preview diagnostics controller
```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,OpsRecoveryControllerSecurityTest test
```
- Result: PASS

3. Backend compile check
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

4. Frontend new service lint check
```bash
cd ecm-frontend
npx eslint src/services/opsRecoveryService.ts
```
- Result: PASS

5. Frontend build check
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

## Verified behaviors
- Non-admin user receives `403` on all `/api/v1/ops/recovery/*` endpoints.
- Admin can call `queue-by-reason` and receive unified structured payload.
- `dry-run` predicts `SKIPPED` for permanent failures without force.
- Unsupported `domain` returns `400` with explicit error message.

## Known limits (expected in this phase)
- Control-plane currently routes only `PREVIEW` domain.
- Dry-run estimation intentionally avoids side effects and does not execute enqueue.
