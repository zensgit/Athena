# Phase 367ZZE: Rendition Registry Dialog For Search And Browser

## Goal
- Extend the definition-backed rendition registry from preview/detail-only surfaces into Athena's two highest-frequency operator work areas.
- Let operators inspect rendition applicability, dependencies, state, and available content without leaving search results or the file browser.

## Scope
- Shared frontend dialog for node rendition definitions
- `SearchResults`
- `FileList`

## Design
- Added a shared `RenditionDefinitionDialog` that lazily loads `/nodes/{nodeId}/renditions/definitions`.
- The dialog shows, per definition:
  - effective display state
  - generation mode
  - dependency rendition
  - target mime type
  - applicability reason
  - open action for available renditions with a content URL
- Added a `Rendition Registry` action to `SearchResults` document cards.
- Added a `View Rendition Registry` action to the `FileList` context menu.

## Why This Matters
- Athena operators can now inspect rendition definition/applicability from browse and search, not only from preview/detail-heavy flows.
- This continues the shift from opaque preview fields toward explicit rendition registry semantics.
- It strengthens day-to-day operator ergonomics in a way the reference implementations often leave fragmented.
