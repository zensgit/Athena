# P4 PR-30 Governed Operations Failure Drilldown Design

## Goal

Turn the existing governed-operations failure counts into actionable recent-job queues without changing any backend API.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

The RM dashboard already exposes:

- failed governed import count
- failed governed transfer count
- recent governed import jobs
- recent governed transfer jobs

But the failure counts are not actionable. Admins can see that failures exist, but they cannot immediately switch the recent-job tables into a failure-focused review queue.

## Design

`PR-30` keeps the RM API authoritative and only adds local filtering on top of the existing telemetry payload.

Delivered UI behavior:

- add local import-table filter state: `all | active | failed`
- add local transfer-table filter state: `all | active | failed`
- add `Review recent failures` CTA to `Failed Governed Imports`
- add `Review recent failures` CTA to `Failed Governed Transfers`
- clicking either CTA scrolls to `Governed Operations` and switches the corresponding table to `failed`
- both recent-job tables display filter chips and filtered count copy
- both recent-job tables distinguish between:
  - no jobs returned at all
  - jobs exist, but no rows match the current filter

## Status Semantics

Import jobs:

- `active` is derived from `status`
- `failed` is derived from `status`

Transfer jobs:

- `active` is derived from either `status` or `transportStatus`
- `failed` is derived from either `status` or `transportStatus`

This keeps import and transfer semantics separate instead of forcing them into one shared status model.

## Non-Goals

- no new backend endpoints
- no changes to telemetry payload shape
- no full-history failure browser
- no cross-page navigation

## Risks

- recent-job filters only act on the telemetry payload currently loaded into the page; they do not represent complete historical failure search
- failure counts on the governance cards may exceed the filtered row count if older failed jobs are outside the current telemetry window
- import and transfer failure semantics remain intentionally independent

## Result

This slice makes the existing RM operations surface actionable without expanding the repository or API contract.
