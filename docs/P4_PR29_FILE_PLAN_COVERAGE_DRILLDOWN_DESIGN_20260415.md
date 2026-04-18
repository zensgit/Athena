# P4 PR-29 File Plan Coverage Drilldown Design

## Goal

Turn the existing `Outside File Plan` RM signal into an actionable front-end review queue, using only the data already loaded on the RM admin page.

## Scope

`PR-29` covers:

- front-end-only file-plan coverage derivation for declared records
- declared-record quick filter for `Outside File Plan`
- governance-health drilldown from `Outside File Plan`
- lightweight file-plan coverage display in the declared-records table
- targeted page regression coverage

`PR-29` does not cover:

- new backend coverage endpoints
- persisted RM file-plan coverage metadata on records
- new search facets or browse-page filters
- non-path-based reconciliation or background repair jobs

## Recommendation

Keep this slice purely front-end and derived.

Use the already-loaded file-plan list as the authoritative coverage boundary and match declared-record paths against the deepest visible file-plan path. That gives operators an actionable queue without introducing a new backend seam.

## Why This Slice Is Safe

- reuses existing `listRecords()` and `listFilePlans()` payloads
- avoids inventing a new backend concept for coverage
- stays aligned with the current RM page as an operator/admin surface
- creates immediate value for the existing `Outside File Plan` summary and health signals

## Frontend Design

### Coverage Derivation

For each declared record:

- normalize the record path
- normalize visible file-plan paths
- pick the deepest matching file plan where:
  - `record.path === filePlan.path`, or
  - `record.path.startsWith(filePlan.path + "/")`

If no file plan matches, the record is treated as `Outside File Plan`.

### Declared Records Filters

Extend the existing quick-filter state with:

- `outsideFilePlan`

### Governance Health Drilldown

For the `Outside File Plan` signal only, expose `Review coverage` that:

- switches the declared-records filter to `outsideFilePlan`
- scrolls toward the declared-records table when supported

### Coverage Column

Add a thin `File Plan Coverage` column to the declared-records table:

- covered records show the matched file-plan name
- uncovered records show `Outside File Plan`

## Files

Frontend production:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`

Frontend tests:

- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

## Outcome

After `PR-29`, the RM dashboard no longer stops at the `Outside File Plan` count. Operators can open a coverage review queue and immediately see which declared records still sit outside any visible file plan.
