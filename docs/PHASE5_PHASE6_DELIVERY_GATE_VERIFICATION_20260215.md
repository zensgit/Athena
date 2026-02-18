# Phase 5/6 Delivery Gate (One-Command) - Verification

## Date
2026-02-15 (updated 2026-02-18)

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
   - `12 passed`
2. `scripts/phase5-fullstack-smoke.sh`
   - `1 passed`
3. `scripts/phase6-mail-automation-integration-smoke.sh`
   - `1 passed`
4. `scripts/phase5-search-suggestions-integration-smoke.sh`
   - `1 passed`
5. `ecm-frontend/e2e/p1-smoke.spec.ts`
   - `5 passed`, `1 skipped`

## Notes
- `p1-smoke` skips mail-rule preview assertion when no preview-capable rule is available in sampled data.
- This gate intentionally combines mocked and real-backend checks to reduce release-day blind spots.
- With latest gate hardening, static full-stack target can trigger prebuilt sync based on `ECM_SYNC_PREBUILT_UI` policy (default `auto`).
