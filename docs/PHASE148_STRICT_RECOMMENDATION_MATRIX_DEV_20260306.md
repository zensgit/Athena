# Phase 148: Strict Recommendation Matrix Automation (Dev)

## Date
2026-03-06

## Scope
- Add a dedicated strict-mode matrix runner for mocked gate workflows.
- Keep implementation isolated to new script/docs artifacts.
- Do not modify `scripts/phase5-phase6-delivery-gate.sh` or `scripts/phase5-regression.sh`.

## New Artifact
- `scripts/phase5-strict-recommendation-matrix.sh`

## Design
- The matrix runner executes delivery-gate scenarios and validates each scenario with:
  - expected return code,
  - extracted key behavior lines from logs,
  - scenario pass/fail status.
- Artifacts are written under:
  - `PHASE5_STRICT_MATRIX_OUTPUT_DIR` (default `/tmp/phase5-strict-recommendation-matrix-<utc-ts>`)
  - per-scenario raw log + cleaned log + key-line report,
  - per-scenario phase5 summary JSON output (for mocked regression scenarios).

## Scenario Matrix
1. `baseline_sufficient_sample`
- Mode: `mocked`
- Intent: sufficient sample baseline with strict enabled and high thresholds.
- Expected RC: `0`
- Key checks:
  - hotspot recommendation `p95` line,
  - flaky recommendation `p90` line,
  - recovery guard warnings `0`,
  - gate success line.

2. `low_confidence_forced_strict_fail`
- Mode: `mocked`
- Intent: force low-confidence recommendations (`min-sample=999`) with strict-fail thresholds (`hotspot=0.1s`, `flaky=1`).
- Expected RC: `1` (delivery gate layer failure)
- Key checks:
  - low-confidence hotspot recommendation line,
  - low-confidence flaky recommendation line,
  - strict hotspot threshold failure line,
  - fast-layer gate failure line.

3. `edge_guard_missing_events_preflight`
- Mode: `preflight`
- Intent: edge guard path with missing expected-events file and strict registry preflight.
- Expected RC: `1`
- Key checks:
  - recovery registry validation start,
  - missing events file warning,
  - strict preflight fail hint,
  - fast-layer gate failure line.

## Usage
```bash
bash scripts/phase5-strict-recommendation-matrix.sh
```

## Notes
- Runtime is primarily driven by the two `mocked` scenarios (frontend build + playwright).
- The preflight edge scenario is fast and does not run full mocked regression.
