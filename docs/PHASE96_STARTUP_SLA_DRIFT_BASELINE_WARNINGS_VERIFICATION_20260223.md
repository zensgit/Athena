# Phase 96: Startup SLA Drift Baseline Warnings Verification

## Date
2026-02-23

## Scope
- Verify startup SLA drift-vs-baseline summary in mocked regression output.
- Verify delivery gate failure hints include drift-warning guidance on controlled failure.
- Reconfirm standard mocked gate path remains green.

## Commands and Results

1. Mocked regression gate (normal path)
```bash
bash scripts/phase5-regression.sh
```
- Result: PASS (`23 passed`)
- Confirmed output:
  - `phase5_regression: startup SLA drift vs baseline`
  - `phase5_regression: startup SLA drift warning count: 0`

2. Mocked delivery gate (normal path)
```bash
DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS

3. Controlled failure path (force SLA + baseline drift warnings)
```bash
ECM_E2E_STARTUP_LOGIN_SLA_MS=100 \
ECM_E2E_STARTUP_BROWSE_SLA_MS=100 \
ECM_STARTUP_SLA_BASELINE_LOGIN_MS=100 \
ECM_STARTUP_SLA_BASELINE_BROWSE_MS=100 \
DELIVERY_GATE_MODE=mocked PW_WORKERS=1 \
bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: expected FAIL
- Confirmed output:
  - `phase5_regression: startup SLA drift warning count: 2`
  - gate startup hints include:
    - `Startup latency drift warnings detected. Compare against baseline and investigate runtime variance/regression.`

4. Integration diagnostics sanity (expected dependency-missing failures)
```bash
ECM_SYNC_PREBUILT_UI=false FULLSTACK_ALLOW_STATIC=1 bash scripts/phase70-auth-route-matrix-smoke.sh
```
- Result: expected FAIL

```bash
DELIVERY_GATE_MODE=integration ECM_SYNC_PREBUILT_UI=false PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh
```
- Result: expected FAIL

## Conclusion
- Startup SLA drift baseline warnings are now visible in mocked gate output.
- Delivery gate failure hints now include baseline-drift diagnosis when warning count is non-zero.
