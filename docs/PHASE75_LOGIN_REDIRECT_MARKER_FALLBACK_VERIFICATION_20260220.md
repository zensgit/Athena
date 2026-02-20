# Phase 75: Login Redirect-Failure Marker Fallback Verification

## Date
2026-02-20

## Scope
- Verify login warning behavior remains correct for existing redirect-failed path.
- Verify marker-only fallback warning path on `/login`.
- Verify stale-marker cleanup behavior.
- Verify auth/route matrix smoke remains green with the new fallback case.

## Commands and Results

1. Login unit tests
```bash
cd ecm-frontend
CI=1 npm test -- --runTestsByPath src/components/auth/Login.test.tsx
```
- Result: PASS (`12 passed`)

2. Auth/route matrix (local source build target)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 \
npx playwright test e2e/auth-route-recovery.matrix.spec.ts --project=chromium --workers=1
```
- Result: PASS (`5 passed`)

3. Phase70 smoke with local source build target
```bash
ECM_UI_URL=http://localhost:3000 \
bash scripts/phase70-auth-route-matrix-smoke.sh
```
- Result: PASS (`5 passed`)

4. Frontend lint
```bash
cd ecm-frontend
npm run lint
```
- Result: PASS

## Environment Note
- Local default auto-detected e2e target may resolve to `http://localhost:5500` (prebuilt/static instance).
- For this phase, fallback coverage validation requires latest source behavior; therefore verification was pinned to `ECM_UI_URL=http://localhost:3000`.

## Conclusion
- Login now consistently surfaces redirect-failure guidance for marker-only fallback state.
- Stale redirect markers are cleaned without noisy warnings.
- Unit and matrix e2e regression coverage are both green.
