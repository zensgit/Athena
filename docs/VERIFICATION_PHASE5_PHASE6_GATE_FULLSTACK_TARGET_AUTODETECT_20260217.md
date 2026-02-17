# Verification: Phase5/Phase6 Gate Fullstack Target Auto-Detect (2026-02-17)

## Scope
- Verify `scripts/phase5-phase6-delivery-gate.sh` full-stack UI auto-detect behavior.
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
  - mocked regression gate: `10 passed`
  - full-stack admin smoke: `1 passed`
  - phase6 mail integration smoke: `1 passed`
  - phase5 search suggestions integration smoke: `1 passed`
  - p1 smoke: `3 passed, 1 skipped`

## Conclusion
- Auto-detect logic works as designed.
- Existing gate behavior and pass criteria remain intact.
