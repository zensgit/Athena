# Next 7-Day Plan - Auth/Search Recovery Hardening - 2026-02-19

## Objective
- Continue the Phase 64-67 stream to complete auth recovery observability, search recovery ergonomics, and deterministic regression behavior.

## Day 1 (Completed): Settings Debug Toggle
- Scope:
  - Expose local auth-recovery debug switch in Settings.
  - Add helper API for local storage override.
  - Extend mocked settings regression test.
- Deliverables:
  - `docs/PHASE67_SETTINGS_AUTH_RECOVERY_DEBUG_TOGGLE_DEV_20260219.md`
  - `docs/PHASE67_SETTINGS_AUTH_RECOVERY_DEBUG_TOGGLE_VERIFICATION_20260219.md`

## Day 2 (Completed): Search Error Taxonomy + Action Mapping
- Scope:
  - Standardize recoverable search error mapping (`retry`, `back`, `advanced`) by error category.
  - Keep inline error actions consistent between `/search` and `/search-results`.
- Planned code areas:
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - `ecm-frontend/src/pages/SearchResults.tsx`
  - `ecm-frontend/src/utils/searchErrorUtils.ts` (new)
- Verification:
  - unit tests for error mapping utility
  - mocked Playwright regression for user-facing actions
- Deliverables:
  - `docs/PHASE68_SEARCH_ERROR_TAXONOMY_RECOVERY_MAPPING_DEV_20260219.md`
  - `docs/PHASE68_SEARCH_ERROR_TAXONOMY_RECOVERY_MAPPING_VERIFICATION_20260219.md`

## Day 3 (Completed): Preview Failure Operator Loop
- Scope:
  - Improve preview-failure action feedback (batch retry/rebuild progress + reason grouping polish).
  - Ensure unsupported/permanent gating text remains explicit.
- Planned code areas:
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - `ecm-frontend/src/utils/previewStatusUtils.ts`
- Verification:
  - unit tests for summary/gating logic
  - mocked regression assertions
- Deliverables:
  - `docs/PHASE69_PREVIEW_FAILURE_OPERATOR_LOOP_DEV_20260219.md`
  - `docs/PHASE69_PREVIEW_FAILURE_OPERATOR_LOOP_VERIFICATION_20260219.md`

## Day 4 (Completed): Auth/Route E2E Matrix
- Scope:
  - Add deterministic E2E matrix for:
    - session expired
    - unknown route fallback
    - auto-login pause window
    - keycloak redirect terminal state
- Planned code areas:
  - `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
  - `scripts/phase70-auth-route-matrix-smoke.sh`
- Verification:
  - `bash scripts/phase70-auth-route-matrix-smoke.sh`
  - `npx playwright test e2e/auth-route-recovery.matrix.spec.ts --project=chromium --workers=1`
- Deliverables:
  - `docs/PHASE70_AUTH_ROUTE_E2E_MATRIX_DEV_20260219.md`
  - `docs/PHASE70_AUTH_ROUTE_E2E_MATRIX_VERIFICATION_20260219.md`

## Day 5 (Completed): Regression Gate Layering
- Scope:
  - Split fast mocked gate and slower integration gate outputs for clearer CI signal.
  - Improve failure reason output with one-line summary per failed spec.
- Planned code areas:
  - `scripts/phase5-regression.sh`
  - `scripts/phase5-phase6-delivery-gate.sh`
  - `scripts/sync-prebuilt-frontend-if-needed.sh` (reuse only)
- Verification:
  - `DELIVERY_GATE_MODE=mocked bash scripts/phase5-phase6-delivery-gate.sh`
  - `DELIVERY_GATE_MODE=all bash scripts/phase5-phase6-delivery-gate.sh`
  - `DELIVERY_GATE_MODE=mocked PW_PROJECT=does-not-exist bash scripts/phase5-phase6-delivery-gate.sh`
- Deliverables:
  - `docs/PHASE71_REGRESSION_GATE_LAYERING_DEV_20260219.md`
  - `docs/PHASE71_REGRESSION_GATE_LAYERING_VERIFICATION_20260219.md`

## Day 6: Failure Injection Coverage
- Scope:
  - Add focused mocked scenarios for:
    - transient refresh failure (no forced logout)
    - terminal refresh failure (logout/redirect)
    - search backend temporary failure with retry success
- Planned code areas:
  - `ecm-frontend/src/services/authService.test.ts`
  - `ecm-frontend/src/services/api.test.ts`
  - `ecm-frontend/e2e/*mock*.spec.ts`
- Verification:
  - targeted unit + mocked e2e pass

## Day 7: Release Closure
- Scope:
  - Consolidate release notes, docs index, and a single verification rollup.
  - Ensure all newly added docs are linked and searchable.
- Deliverables:
  - release summary MD
  - verification rollup MD

## Exit Criteria
1. All new/updated tests pass (unit + mocked regression + delivery gate).
2. No unknown-route blank screen regressions.
3. Search failure flows consistently expose recovery actions.
4. Auth recovery debug tooling is discoverable and controlled via UI.
