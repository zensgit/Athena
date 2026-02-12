# Phase 2 Day 4 (P1) - Search Snippet Enrichment (Design)

Date: 2026-02-12

## Goal

Improve search result readability and parity with “market” ECM UX by surfacing key context inline:
- Path (breadcrumb-style) so users understand where a hit lives
- Creator
- Match fields (“Matched in …”) in a compact, scannable row

This should apply consistently across:
- Search Results (`/search-results`)
- Advanced Search (`/search`)

## Scope

- Frontend only (`ecm-frontend`)
- No API or schema changes
- Add a focused Playwright spec to assert the fields render

Out of scope:
- New search fields in Elasticsearch or backend mapping changes
- Changing relevance scoring or highlight computation

## UX / Behavior

### Path display

We display a breadcrumb-style path (truncated to last N segments) and avoid duplicating the filename:
- Input: `node.path` / `result.path` (server-provided path like `/Root/Documents/.../file.ext`)
- Output: `Root / Documents / ...` (joined with ` / `)
- If the last segment equals the node name, drop it.
- If too long, show `... / last / segments`

The rendered label is a single-line caption (`noWrap`) with tooltip containing the full raw path.

### Creator display

Use existing fields:
- Search Results uses `node.creator`
- Advanced Search uses `result.createdBy` (from faceted search results)

Display:
- `By {creator}` appended to the path line: `{breadcrumb} | By admin`

### Match fields

Already present:
- Search Results: “Matched in” chips via `resolveMatchFields(node)`
- Advanced Search: chips are computed from `result.matchFields` or `result.highlights` keys

No change in semantics; keep the layout stable and compact.

## Implementation

### Shared helper

Add a small utility to format breadcrumb paths:
- `ecm-frontend/src/utils/pathDisplayUtils.ts`
- `formatBreadcrumbPath(rawPath, { nodeName, maxSegments })`

### Files touched

- `ecm-frontend/src/utils/pathDisplayUtils.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

Automation:
- `ecm-frontend/e2e/search-snippet-enrichment.spec.ts`

## Compatibility

- No migrations
- No breaking UI changes: title remains primary; added line is a caption

