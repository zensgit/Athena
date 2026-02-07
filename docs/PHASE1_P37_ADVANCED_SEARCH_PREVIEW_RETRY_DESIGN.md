# Phase 1 P37: Advanced Search Preview Retry Parity Design

## Date
2026-02-07

## Background
`/api/v1/search/faceted` returned search hits without `previewStatus` and `previewFailureReason`.
As a result:

- Advanced Search cards showed preview chips inconsistently.
- Advanced Search could not reliably offer the same preview-retry tooling as Search Results.

## Goals

- Keep faceted search payload aligned with full-text search payload for preview fields.
- Add retry parity on `AdvancedSearchPage` for failed previews.
- Make e2e coverage deterministic for this flow (avoid Keycloak UI redirect flakiness).

## Scope

- Backend:
  - `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
  - `ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java`
- Frontend:
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - `ecm-frontend/e2e/search-preview-status.spec.ts`

## Design Decisions

1. Preserve backend API shape, only fill already-existing fields.

- `SearchResult` already contains `previewStatus` and `previewFailureReason`.
- `FacetedSearchService#toSearchResult` now maps both fields from `NodeDocument`.

2. Reuse Search Results retry interaction model in Advanced Search.

- Per-card retry action for `FAILED` preview.
- Batch retry action for all failed results in current page.
- Retry-by-reason quick actions based on grouped `previewFailureReason`.
- Queue detail display (`Attempts`, `Next retry`) for operator feedback.

3. Stabilize e2e around authentication.

- For this spec, use token-seeded local storage session (`ecm_e2e_bypass`) instead of UI login redirects.
- This keeps verification focused on preview status/retry behavior rather than Keycloak page navigation.

## UI/Behavior Changes

- Advanced Search now shows:
  - `Retry failed previews`
  - `Force rebuild failed previews`
  - `Retry "<reason>" (count)` buttons
  - Per-result retry icon button (`aria-label="Retry preview"`) when status is failed
  - Queue hint text with next retry timestamp

## API/Contract Notes

- No new endpoint.
- No request/response schema change.
- Existing `SearchResult` fields now populated consistently in faceted responses:
  - `previewStatus`
  - `previewFailureReason`

## Risks and Mitigations

- Risk: batch retry may run many queue calls if page size grows.
  - Mitigation: current page is bounded (`size: 10` in this page).
- Risk: auth redirect instability in UI-driven e2e.
  - Mitigation: token-seeded bypass in this spec.

