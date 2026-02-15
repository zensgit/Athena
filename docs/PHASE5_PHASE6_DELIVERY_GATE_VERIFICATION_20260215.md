# Phase 5/6 Delivery Gate (One-Command) - Verification

## Date
2026-02-15

## Command

```bash
ECM_UI_URL_MOCKED=http://localhost:5500 \
ECM_UI_URL_FULLSTACK=http://localhost \
ECM_API_URL=http://localhost:7700 \
bash scripts/phase5-phase6-delivery-gate.sh
```

## Result
- PASS

## Verified Chain
1. `scripts/phase5-regression.sh`
   - `9 passed`
2. `scripts/phase5-fullstack-smoke.sh`
   - `1 passed`
3. `scripts/phase6-mail-automation-integration-smoke.sh`
   - `1 passed`
4. `ecm-frontend/e2e/p1-smoke.spec.ts`
   - `3 passed`, `1 skipped`

## Notes
- `p1-smoke` skips mail-rule preview assertion when no preview-capable rule is available in sampled data.
- This gate intentionally combines mocked and real-backend checks to reduce release-day blind spots.
