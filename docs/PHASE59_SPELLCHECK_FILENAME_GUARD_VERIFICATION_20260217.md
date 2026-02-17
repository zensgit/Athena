# Phase 59 - Spellcheck Filename Guard (Verification) - 2026-02-17

## Verification Scope
- Validate that filename/id-style queries skip spellcheck suggestion fetch.
- Ensure existing typo spellcheck flow still works.

## Commands and Results

1. Unit tests
```bash
cd ecm-frontend
CI=1 npm test -- --watch=false --runTestsByPath src/utils/searchFallbackUtils.test.ts
```
- Result: PASS
  - `7 passed, 7 total`
  - Includes new skip/non-skip spellcheck guard cases.

2. Mocked regression gate
```bash
./scripts/phase5-regression.sh
```
- Result: PASS
  - `11 passed`
  - Includes:
    - Existing spellcheck + save-search positive path
    - New filename-like query guard path:
      - no spellcheck request fired
      - no “Did you mean / Search instead for” banner shown

3. P1 smoke (real backend/static UI compatibility assertion)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
KEYCLOAK_REALM=ecm \
npx playwright test e2e/p1-smoke.spec.ts -g "filename-like query skips spellcheck call" --project=chromium --workers=1
```
- Result: PASS (`1 passed`)
- Assertion: filename-like query does not show spellcheck suggestion banners.

4. Strong request-level assertion on branch build (optional)
```bash
cd ecm-frontend
npx serve -s build -l 3000
# in another shell
ECM_UI_URL=http://localhost:3000 \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
KEYCLOAK_REALM=ecm \
ECM_E2E_ASSERT_SPELLCHECK_SKIP=1 \
npx playwright test e2e/p1-smoke.spec.ts -g "filename-like query skips spellcheck call" --project=chromium --workers=1
```
- Result: PASS (`1 passed`)
- Assertion: spellcheck request count is exactly `0`.

## Behavior Checks
- Query `e2e-preview-failure-1770563224443.bin`:
  - Spellcheck lookup is skipped.
  - Search results remain available.
- Query typo like `spelcheck`:
  - Spellcheck suggestions still appear as expected.

## Conclusion
- Filename guard works as designed.
- Existing search suggestion UX remains intact for natural-language typo correction.
