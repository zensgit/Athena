# Phase 249 - Ops Recovery Dry-run Stale Plan Guard (Dev)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Goals

1. Prevent executing recovery with stale dry-run output after operators change filters/mode.
2. Keep dry-run and execute parameters fully consistent (reason/retryable/maxDocs/window/force).
3. Surface clear UI guidance when dry-run must be rerun.

## 2. Implementation

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

- Added dry-run request signature state:
  - `dryRunRequestSignature`
- Added normalized parameter model used by both run/execute:
  - normalized reason
  - normalized retryable
  - normalized max docs
  - normalized force (auto-false for clear mode)
- Added stale-plan computation:
  - `dryRunPlanStale` compares current signature vs signature captured at last successful dry-run.
- Added execute guard:
  - if stale, block execution and show warning toast.
- Updated execute button enablement:
  - disabled while stale, loading, or no dry-run result.
- Added inline warning alert in dry-run result panel:
  - “Dry-run plan is stale. Run Dry-run again before Execute Recovery.”

## 3. UX Behavior

- Operator flow now enforces:
  1. Run dry-run with current conditions.
  2. Execute only while those conditions remain unchanged.
- Switching mode/filter/reason immediately marks the plan stale until rerun.

## 4. Design Notes

- This closes an ops risk where previous dry-run estimates could be misinterpreted as valid for newly changed criteria.
- Guardrail is frontend-side and non-breaking to backend APIs.
