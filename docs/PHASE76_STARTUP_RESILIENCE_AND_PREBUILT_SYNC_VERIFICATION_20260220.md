# Phase 76: Startup Resilience and Prebuilt Sync Dirty-Worktree Guard Verification

## Date
2026-02-20

## Scope
- Verify startup auth/route matrix remains green after storage safety hardening.
- Verify full delivery gate remains green after startup and sync changes.
- Verify `sync-prebuilt-frontend-if-needed.sh` auto mode rebuilds on dirty frontend worktree.

## Commands and Results

1. Targeted frontend tests
```bash
cd ecm-frontend
CI=1 npm test -- --runTestsByPath \
  src/App.test.tsx \
  src/components/auth/Login.test.tsx \
  src/services/authBootstrap.test.ts
npm run lint
```
- Result: PASS
- Test summary: `3 suites passed`, `21 tests passed`

2. Auth/route matrix on source build target
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 \
npx playwright test e2e/auth-route-recovery.matrix.spec.ts --project=chromium --workers=1
```
- Result: PASS (`6 passed`)

3. Phase70 script on source build target
```bash
ECM_UI_URL=http://localhost:3000 \
bash scripts/phase70-auth-route-matrix-smoke.sh
```
- Result: PASS (`6 passed`)

4. Full delivery gate (forced prebuilt sync)
```bash
ECM_SYNC_PREBUILT_UI=1 DELIVERY_GATE_MODE=all PW_WORKERS=1 \
bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS
- Layer summary:
  - fast mocked: PASS (`14 passed`)
  - integration:
    - full-stack prebuilt sync check: PASS
    - full-stack admin smoke: PASS
    - phase6 mail integration smoke: PASS
    - phase5 search suggestions integration smoke: PASS
    - phase70 auth-route matrix smoke: PASS (`6 passed`)
    - p1 smoke: PASS (`5 passed`, `1 skipped`)

5. Dirty-worktree stale detection
```bash
ECM_SYNC_PREBUILT_UI=auto \
bash scripts/sync-prebuilt-frontend-if-needed.sh http://localhost
```
- Result: PASS
- Observed log: `stale prebuilt detected (dirty_worktree), rebuilding for http://localhost`

## Conclusion
- Startup path is resilient under storage-operation exceptions.
- Auth/route matrix now explicitly guards startup recoverability under storage failures.
- Auto prebuilt sync no longer treats dirty frontend working tree as up-to-date.
