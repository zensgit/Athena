# P4 PR-28 RM Dashboard Drilldown Design

## Goal

Turn the existing RM summary and governance-health numbers into a thin, actionable front-end drilldown without changing any backend contract.

## Scope

`PR-28` covers:

- declared-record quick filters on the RM admin page
- uncategorized review queue wiring from the governance-health section
- front-end-only filtering of the existing declared-record list
- targeted page regression coverage

`PR-28` does not cover:

- new backend filters or endpoints
- outside-file-plan record drilldown beyond the current count card
- trend charts, sparklines, or persistent time-series state
- browse-page or preview-page RM routing changes

## Recommendation

Do not add synthetic trend state or chart dependencies for this slice.

The safer and more useful drilldown is to make the current `Declared Records` table directly actionable:

- `All`
- `Uncategorized`
- `Categorized`

Then wire the existing `Uncategorized Records` governance alert to jump operators into the uncategorized queue.

## Why This Slice Is Better Than Sparkline History

- uses authoritative data already loaded on the page
- does not invent session-local trend semantics
- creates operator value immediately
- avoids extra chart dependencies and layout churn

## Frontend Design

### Declared Records Filter State

Add a local filter state on `RecordsManagementPage`:

- `all`
- `uncategorized`
- `categorized`

Filtering is derived from the already-loaded `records` array and existing `recordCategoryId` fields.

### Governance Health Drilldown

For the `Uncategorized Records` alert only, expose a thin `Review queue` action that:

- switches the declared-records filter to `uncategorized`
- scrolls the operator toward the declared-records table when supported

### Declared Records Header

Show:

- current filter summary
- counts for `All`, `Uncategorized`, and `Categorized`
- empty-state copy when a filter yields zero rows

## Files

Frontend production:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`

Frontend tests:

- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

## Outcome

After `PR-28`, the RM dashboard no longer stops at passive counts. Operators can jump straight into the uncategorized queue and work from the existing declared-record table without any backend change.
