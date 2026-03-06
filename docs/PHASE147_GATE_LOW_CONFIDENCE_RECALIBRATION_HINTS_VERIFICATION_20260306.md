# Phase 147 Verification: Delivery Gate Low-Confidence Recalibration Command Hints

## Date
2026-03-06

## Verification Commands
1. Syntax check:
```bash
bash -n scripts/phase5-phase6-delivery-gate.sh
```

2. Forced low-confidence strict failure:
```bash
tmp_dir="$(mktemp -d /tmp/gate-phase5-low-confidence.XXXXXX)"
DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PRINT_EXECUTION_PLAN=0 \
DELIVERY_GATE_PHASE5_SUMMARY_DIR="${tmp_dir}" \
DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1 \
DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=0.1 \
DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=1 \
PW_WORKERS=1 \
bash scripts/phase5-phase6-delivery-gate.sh \
  --phase5-hotspot-recommend-percentile=0.8 \
  --phase5-hotspot-recommend-padding-sec=0.5 \
  --phase5-hotspot-recommend-min-sample=999 \
  --phase5-flaky-recommend-percentile=0.6 \
  --phase5-flaky-recommend-step=2 \
  --phase5-flaky-recommend-min-sample=999
```

3. Default mocked success:
```bash
tmp_dir="$(mktemp -d /tmp/gate-phase5-pass10.XXXXXX)"
DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PRINT_EXECUTION_PLAN=0 \
DELIVERY_GATE_PHASE5_SUMMARY_DIR="${tmp_dir}" \
PW_WORKERS=1 \
bash scripts/phase5-phase6-delivery-gate.sh
```

4. Plan JSON fields:
```bash
DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PRINT_EXECUTION_PLAN=0 \
bash scripts/phase5-phase6-delivery-gate.sh --plan --plan-format=json
```

5. Invalid min-sample rejection:
```bash
bash scripts/phase5-phase6-delivery-gate.sh --plan --phase5-hotspot-recommend-min-sample=0
```

## Results
- Syntax check: PASS.
- Low-confidence strict run:
  - exit code: `RC=1`
  - strict hints include:
    - `Hotspot recommendation confidence: LOW (sample 30 < min 999).`
    - `Hotspot recalibration hint: rerun with recommend min sample <= 30.`
    - `Flaky-risk recommendation confidence: LOW (sample 14 < min 999).`
    - `Flaky-risk recalibration hint: rerun with recommend min sample <= 14.`
  - strict suggestions now include recalibration commands before relax commands:
    - `DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=30` (priority 1)
    - `DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE=14` (priority 3)
    - existing relax commands remain:
      - hotspot threshold relax to `0.6` (priority 2)
      - flaky-risk threshold relax to `3` (priority 4)
- Default mocked run:
  - exit code: `RC=0`
  - `phase5_phase6_delivery_gate: ok`
  - default min-sample controls unchanged (`5` and `3`)
- Plan JSON:
  - exit code: `RC=0`
  - includes:
    - `phase5_strict_hotspot_recommend_min_sample`
    - `phase5_strict_flaky_recommend_min_sample`
- Invalid min-sample:
  - exit code: `RC=1`
  - emits positive-integer validation error for hotspot min-sample.

## Conclusion
- Delivery gate strict diagnostics now provide a safer first remediation path under LOW-confidence recommendations by guiding operators to recalibrate sample controls before relaxing strict thresholds.
