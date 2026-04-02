# Phase368S: Advanced Search Association Relation Details Convergence

## Goal

Consume the newly delivered `Phase368R` node association APIs from an existing high-signal operator surface instead of leaving them backend-only.

The target surface for this slice is the `AdvancedSearchPage` relations detail panel, which already acts as Athena's richest document relationship summary surface.

## Problem

Before this slice:

- `Phase368R` had already added peer association and secondary-child APIs.
- `nodeService` already exposed those APIs.
- but `AdvancedSearchPage` still only displayed:
  - parent chain
  - relation sources
  - relation targets
  - versions
  - checkout graph
  - rendition relations

That meant secondary-child / secondary-parent associations existed in the platform but were still invisible from the main relations detail work surface.

## Scope

- Extend `AdvancedSearchPage` relations detail loading to fetch:
  - `getSecondaryChildren(nodeId)`
  - `getSecondaryParents(nodeId)`
- Render both association categories in the relations detail block.
- Extract a small shared formatter utility for node-association edge text.

## Implementation

### 1. Shared node association formatter

Added `ecm-frontend/src/utils/nodeAssociationUtils.ts`:

- `summarizeNodeAssociationEdges(edges, role, limit)`

Supported roles:

- `source`
- `target`
- `secondaryChild`
- `secondaryParent`

This removes yet another page-local string-building branch from `AdvancedSearchPage`.

### 2. Advanced Search relation state expansion

Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:

- added state for:
  - `nodeRelationSecondaryChildren`
  - `nodeRelationSecondaryParents`
- added both collections into:
  - relation detail loading
  - empty/non-empty detection
  - reset/unavailable paths

### 3. Relation detail surface expansion

The relation details block now renders:

- `Secondary children: ...`
- `Secondary parents: ...`

using the shared association edge summarizer.

## Why This Slice

This is the fastest way to turn `Phase368R` from "service capability exists" into "operators can actually see the new association model in a production work surface" without yet introducing a full association editor.

That keeps momentum on the platform line while avoiding a heavy UI jump.
