# P4 PR-73 Plan Consistency Cleanup Design

## Scope

`PR-73` is a documentation-only convergence slice for the `P4` RM plan set.

It does not change product behavior, backend contracts, frontend behavior, or tests. The goal is to remove planning ambiguity introduced by a duplicated thin-slice entry.

## Problem

`PR-68` and `PR-69` were both recorded as:

- thin RM activity contributor report export UI

with the same user-facing scope, same reused backend endpoint, and no distinct product delta. Leaving both as active/complete feature slices makes the execution plan and acceptance ledger inconsistent with the actual code history.

## Decision

- `PR-68` remains the authoritative shipped slice for activity-contributor report export UI
- `PR-69` is retained only as a historical duplicate planning artifact
- `PR-69` is marked as superseded / merged into `PR-68`
- plan and acceptance documents are updated to reflect that `PR-69` did not introduce a distinct product outcome

## Files Updated

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`
- `docs/P4_PR69_RM_ACTIVITY_CONTRIBUTOR_REPORT_EXPORT_UI_DESIGN_20260417.md`
- `docs/P4_PR69_RM_ACTIVITY_CONTRIBUTOR_REPORT_EXPORT_UI_VERIFICATION_20260417.md`

## Non-Goals

- no code changes
- no renumbering of already referenced PR files
- no rewrite of earlier PR content beyond the minimal consistency note
