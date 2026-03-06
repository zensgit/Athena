# Phase 149 Verification: Gate Strict Suggestions Artifact and Confidence Metadata

## Date
2026-03-06

## Verification Commands
1. Syntax checks:
```bash
bash -n scripts/phase5-phase6-delivery-gate.sh
bash -n scripts/phase5-regression.sh
```

2. Low-confidence strict run with structured suggestion artifact:
```bash
tmp_dir="$(mktemp -d /tmp/gate-phase5-low-confidence-p148b.XXXXXX)"
strict_json="${tmp_dir}/strict-suggestions.json"
DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PRINT_EXECUTION_PLAN=0 \
DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=0 \
DELIVERY_GATE_STRICT_SUGGESTIONS_FILE="${strict_json}" \
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

3. Default mocked run:
```bash
tmp_dir="$(mktemp -d /tmp/gate-phase5-pass11.XXXXXX)"
DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PRINT_EXECUTION_PLAN=0 \
DELIVERY_GATE_PHASE5_SUMMARY_DIR="${tmp_dir}" \
PW_WORKERS=1 \
bash scripts/phase5-phase6-delivery-gate.sh
```

4. Plan JSON includes strict suggestion output controls:
```bash
bash scripts/phase5-phase6-delivery-gate.sh --plan --plan-format=json --strict-suggestions-file=/tmp/gate-strict-suggestions.json
```

5. Invalid strict-suggestions JSON flag validation:
```bash
DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=2 bash scripts/phase5-phase6-delivery-gate.sh --plan
```

## Results
- Syntax checks: PASS.
- Low-confidence strict run:
  - exit code: `RC=1`
  - strict hints include reason-aware confidence lines:
    - hotspot: `LOW ... [reason=sample_below_min]`
    - flaky-risk: `LOW ... [reason=sample_below_min]`
  - recalibration hints use observed sample counts:
    - hotspot `<= 30`
    - flaky-risk `<= 14`
  - strict suggestion artifact is written and includes 4 ordered suggestions:
    - hotspot recalibration
    - hotspot threshold relax
    - flaky-risk recalibration
    - flaky-risk threshold relax
  - phase5 summary strict metadata includes:
    - `hotspot_recommendation_confidence_level`
    - `hotspot_recommendation_reason_code`
    - `hotspot_recommended_min_sample`
    - `flaky_recommendation_confidence_level`
    - `flaky_recommendation_reason_code`
    - `flaky_recommended_min_sample`
- Default mocked run:
  - exit code: `RC=0`
  - `phase5_phase6_delivery_gate: ok`
- Plan JSON:
  - exit code: `RC=0`
  - includes:
    - `print_strict_suggestions_json`
    - `strict_suggestions_file`
- Invalid flag:
  - exit code: `RC=1`
  - validation error emitted for `DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON`.

## Conclusion
- Strict remediation output is now consumable in structured JSON form.
- Confidence semantics are normalized and machine-readable across regression summary and gate hints.
