# P4 PR-24 RM Dashboard Deepening Design

## Goal

Improve the RM admin page so operators can identify governance risk and operational trouble on first read instead of parsing raw tables.

This slice intentionally builds on the existing RM summary, operations telemetry, and audit surfaces. It does not introduce a new governance domain.

## Recommendation

Implement `PR-24` as one small full-stack slice:

- backend: enrich RM operations telemetry with failure counts and governance-reason breakdowns
- frontend: turn those signals into a more actionable dashboard on `RecordsManagementPage`

This is higher value than adding more raw tables because the current page already exposes the base data. The gap is prioritization and signal density.

## Scope

`PR-24` should cover:

- governed import failure count
- governed transfer failure count
- import governance-reason breakdown
- transfer governance-reason breakdown
- `Governance Health` section on the RM admin page
- `Top Governance Reasons` section on the governed-operations card

`PR-24` should not cover:

- new RM routes
- new background jobs
- new audit event families
- file-plan/category edit-delete workflows
- charting libraries or heavy visualization dependencies

## Backend Design

### Telemetry Enrichment

Extend `RecordsOperationsTelemetryDto` to include:

- `failedGovernedImportJobCount`
- `failedGovernedTransferJobCount`
- `importGovernanceReasonBreakdown`
- `transferGovernanceReasonBreakdown`

### Failure Semantics

Use repository-owned status logic instead of letting the frontend infer failures:

- import failure:
  - `FAILED`
  - `CANCELED`
- transfer failure:
  - workflow `FAILED`
  - workflow `CANCELED`
  - transport `FAILED`

This keeps state interpretation centralized and avoids duplicating status semantics in the UI.

### Reason Breakdown

Aggregate the existing governance reasons already produced by:

- `classifyImportJob(...)`
- `classifyTransferJob(...)`

Return them as `SummaryBucketDto` lists so the frontend can render them with the same chip/table vocabulary already used elsewhere.

## Frontend Design

### Governance Health

Add a top-level `Governance Health` card to `RecordsManagementPage` that highlights the four most actionable current signals:

- uncategorized records
- records outside any file plan
- failed governed imports
- failed governed transfers

The page should:

- show a success banner when all four are zero
- show a warning banner when one or more need attention
- keep the individual signal cards visible in both states

### Governed Operations

Extend the existing operations section with:

- failed import / failed transfer summary cards
- `Top Import Governance Reasons`
- `Top Transfer Governance Reasons`

This keeps operational troubleshooting on the same page without adding another admin surface.

## Expected Change Surface

Backend:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`

Frontend:

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

No route changes are required.

## Product Outcome

After `PR-24`, an RM admin should be able to answer these questions immediately:

- are declared records still missing category assignment?
- are declared records still outside file-plan governance?
- do governed import/transfer failures need action?
- what governance reason is driving most governed operations right now?

That is the smallest useful upgrade beyond the current summary + telemetry tables.
