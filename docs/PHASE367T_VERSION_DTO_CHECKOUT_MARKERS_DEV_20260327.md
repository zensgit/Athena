# Phase367T: Version DTO Checkout Markers

## Goal

Strengthen Athena’s source-relationship semantics by promoting checkout baseline/current markers into version DTOs and version-history UI, without introducing a full working-copy entity model.

## Scope

- Extend backend `VersionDto` with:
  - `checkoutBaseline`
  - `checkoutCurrent`
- Derive those flags from:
  - document `checkoutBaselineVersionId`
  - document `currentVersion`
- Surface the markers in:
  - node relation versions API
  - frontend `Version` model
  - advanced-search relations details
  - version history dialog

## Design

### Why version markers

Athena already knows:

- who checked out a document
- when checkout happened
- what baseline version checkout started from
- what the current version is

The next useful step is to make those relationships directly visible on the versions themselves. That gives operators a real source/current comparison model instead of a free-form text explanation only.

### Backend behavior

`VersionDto.from(version)` now computes:

- `checkoutBaseline=true` when the version id matches `document.checkoutBaselineVersionId`
- `checkoutCurrent=true` when the version id matches `document.currentVersion.id`

This keeps the marker logic centralized and automatically benefits:

- document version history APIs
- node relation versions APIs
- any other consumer already using `VersionDto`

### Frontend behavior

- `AdvancedSearchPage` relation details now annotate version strings with `[baseline]` / `[current]`.
- `VersionHistoryDialog` now renders `Baseline` and `Current` chips directly in the version label area and compare pickers.

## Why this matters

This is still not a full working-copy graph, but it is a stronger source-relationship model than a single checkout summary line:

- versions themselves know where checkout started
- current state vs checkout baseline becomes directly inspectable
- the same semantics now show up in multiple operator surfaces

## Files

- `ecm-core/src/main/java/com/ecm/core/dto/VersionDto.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to pressure-test whether version-level checkout markers were the smallest next step toward stronger source-relationship semantics. Final implementation and validation were completed in this workspace.
