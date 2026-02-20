# Phase 81: Delivery Gate Startup Diagnostics Hints Verification

## Date
2026-02-20

## Scope
- Verify script syntax and normal success path remain stable.
- Verify startup hint section is printed on controlled startup-related failure.

## Commands and Results

1. Script syntax check
```bash
bash -n scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS

2. Success path (mocked layer only)
```bash
DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS
- Layer summary:
  - fast mocked layer: PASS
  - integration/full-stack layer: not executed

3. Controlled failure path (strict static preflight)
```bash
DELIVERY_GATE_MODE=integration \
ECM_UI_URL_FULLSTACK=http://localhost \
ECM_FULLSTACK_ALLOW_STATIC=0 \
bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: expected FAIL (`EXIT_CODE=1`)
- Verified output contains:
  - `startup diagnostics hints`
  - `Static/prebuilt target may be stale. Prefer dev target (:3000) or run prebuilt sync before rerun.`

## Conclusion
- Delivery gate now emits startup-focused remediation hints on failure without affecting success path behavior.
