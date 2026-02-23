# Phase 91: FolderTree Root Loading Watchdog Verification

## Date
2026-02-22

## Scope
- Verify root-loading watchdog and retry recovery in `FolderTree`.
- Verify regression gate remains stable after adding the new mocked spec.

## Commands and Results

1. Frontend targeted unit suite
```bash
cd ecm-frontend
npm test -- --watch=false --runInBand \
  src/components/auth/Login.test.tsx \
  src/components/auth/PrivateRoute.test.tsx \
  src/utils/searchFallbackUtils.test.ts
```
- Result: PASS (`3 suites`, `34 tests`)

2. Frontend lint
```bash
cd ecm-frontend
npm run lint
```
- Result: PASS

3. Mocked regression gate
```bash
bash scripts/phase5-regression.sh
```
- Result: PASS (`18 passed`)
- Includes:
  - `e2e/folder-tree-root-watchdog.mock.spec.ts`

4. One-command mocked delivery gate
```bash
DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS
- Confirmed:
  - mocked layer executes successfully
  - duration hotspots/flaky-risk summaries still emitted

5. Integration diagnostics sanity (expected dependency-missing failures)
```bash
ECM_SYNC_PREBUILT_UI=false FULLSTACK_ALLOW_STATIC=1 bash scripts/phase70-auth-route-matrix-smoke.sh
```
- Result: expected FAIL
- Confirmed structured hint output for backend health endpoint.

```bash
DELIVERY_GATE_MODE=integration ECM_SYNC_PREBUILT_UI=false PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: expected FAIL
- Confirmed grouped dependency preflight diagnostics (backend/keycloak/ui + remediation hints).

## Conclusion
- Folder tree root bootstrap now has explicit slow-load and failure recovery affordances.
- Default mocked regression remains green with the added resilience scenario.
