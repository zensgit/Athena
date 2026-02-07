# Phase 1 P38: Advanced Search Preview Status Parity Design

## Date
2026-02-07

## Background

P37 delivered retry actions in Advanced Search, but preview diagnostics parity with Search Results was incomplete:

- No preview status count snapshot in Advanced Search.
- No preview status chip filtering in Advanced Search.
- Retry panel was present, but lacked filter-aware context.

## Goals

- Add preview status diagnostics snapshot to Advanced Search.
- Add per-page preview status filtering to Advanced Search.
- Keep retry tooling and status filters coherent in one operator workflow.

## Scope

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/e2e/search-preview-status.spec.ts`

## Design Decisions

1. Current-page filtering model (same as Search Results)

- `selectedPreviewStatuses` added to represent chosen status chips.
- Filtering is intentionally scoped to current loaded page results from `/search/faceted`.
- UI explicitly shows: `Preview status filters apply to the current page only.`

2. Status snapshot chips

- Added counts for:
  - `READY`
  - `PROCESSING`
  - `QUEUED`
  - `FAILED`
  - `PENDING`
- Chips are toggleable multi-select filters with a `Clear` action.

3. Retry flow integration

- Retry panel continues to show:
  - retry all failed
  - force rebuild failed
  - retry by failure reason
  - queue summary
- Retry panel remains based on current search page result set (not hidden by status chip selection), matching operator expectations for bulk remediation.

4. Empty-state handling under filter

- When base results exist but selected preview statuses match none, page shows explicit filtered-empty message instead of blank area.

## Behavioral Impact

- Operators can diagnose preview generation distribution directly in Advanced Search.
- Operators can focus visible results to one or more preview states before drilling into cards.
- Retry controls remain available in the same view for failed documents.

## Risks and Mitigations

- Risk: users assume filter applies to full search corpus.
  - Mitigation: persistent explanatory caption in status panel.
- Risk: state carries over across new searches/pages.
  - Mitigation: clear status selection on each new search submit/page change (`handleSearch` resets selection).

