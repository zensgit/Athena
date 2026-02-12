# Phase 1 P90 Design: Advanced Search Active Criteria Summary

## Background
- Advanced Search dialog has multiple accordion sections.
- When criteria span non-basic sections, users can miss which filters are currently active.
- This leads to confusion and repeated re-open/expand actions during troubleshooting.

## Goal
- Add an always-visible summary block in the dialog that shows active criteria as chips.
- Improve transparency and reduce “dialog looks empty / unclear filters” feedback.

## Scope
- Frontend only (`SearchDialog`).
- Includes Playwright verification.

## Implementation
1. Build active criteria model from current dialog state
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- Added `activeCriteriaChips` via `React.useMemo` combining:
  - basic fields (`name`, `contentType`, `createdBy`)
  - date ranges (`created`, `modified`)
  - `aspects`
  - custom properties
  - tags/categories/correspondents
  - preview statuses
  - size range
  - scope (`folderId/includeChildren`) or `pathPrefix`

2. Render summary UI in dialog content
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- New summary container:
  - title: `Active Criteria (N)`
  - chips list for each active criterion
  - test hook: `data-testid="active-criteria-summary"`

3. End-to-end coverage
- File: `ecm-frontend/e2e/search-dialog-active-criteria-summary.spec.ts` (new)
- Verifies chips appear after selecting criteria across sections.

## UX notes
- Summary is only shown when at least one criterion exists.
- Existing validation and Save/Search enablement behavior is unchanged.

