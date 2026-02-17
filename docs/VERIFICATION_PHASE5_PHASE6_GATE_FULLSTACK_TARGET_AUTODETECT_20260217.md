# Verification: Phase5/Phase6 Gate Fullstack Target Auto-Detect (2026-02-17)

## Scope
- Verify `scripts/phase5-phase6-delivery-gate.sh` full-stack UI auto-detect behavior.
- Verify full-stack static-target policy switch (`ECM_FULLSTACK_ALLOW_STATIC`).
- Confirm complete delivery gate remains green after change.

## Commands
1. Script syntax check
```bash
bash -n scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS

2. End-to-end delivery gate (without setting `ECM_UI_URL_FULLSTACK`)
```bash
PW_PROJECT=chromium PW_WORKERS=1 \
ECM_UI_URL_MOCKED=http://localhost:5515 \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
KEYCLOAK_REALM=ecm \
ECM_E2E_USERNAME=admin \
ECM_E2E_PASSWORD=admin \
./scripts/phase5-phase6-delivery-gate.sh
```

## Result
- PASS
- Observed output includes auto-detect notice:
  - `ECM_UI_URL_FULLSTACK auto-detected (set ECM_UI_URL_FULLSTACK to override)`
- Gate stages:
  - mocked regression gate: `11 passed`
  - full-stack admin smoke: `1 passed`
  - phase6 mail integration smoke: `1 passed`
  - phase5 search suggestions integration smoke: `1 passed`
  - p1 smoke: `4 passed, 1 skipped`

3. Strict full-stack policy (expect failure on static target)
```bash
ECM_FULLSTACK_ALLOW_STATIC=0 ./scripts/phase5-phase6-delivery-gate.sh
```
- Result: EXPECTED FAIL at full-stack smoke target check
  - `detected_mode=static`
  - exit with hint to use `ECM_UI_URL=http://localhost:3000` for branch-accurate E2E.
  - with latest script: failure happens during strict preflight, before `[1/5]`.

4. CI-default strict policy (expect same behavior when UI target is static)
```bash
CI=1 ./scripts/phase5-phase6-delivery-gate.sh
```
- Result: EXPECTED FAIL (same as strict mode), unless `ECM_UI_URL_FULLSTACK` points to dev/proxy target.

## Conclusion
- Auto-detect logic works as designed.
- Existing gate behavior remains intact by default (`ECM_FULLSTACK_ALLOW_STATIC=1`).
- Strict mode is now available to enforce branch-accurate full-stack targets.
- `p1 smoke` stage now uses the same static-target policy check as stages 2-4.
- CI default is stricter for safer branch-accurate gating.
