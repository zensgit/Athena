# P4 PR-26 Rename-Move Semantics Design

## Goal

Define a safe implementation path for the two RM follow-up capabilities still intentionally deferred:

- file plan rename / re-parent
- record category rename / re-parent

This document remains the umbrella design note for `PR-26`.

Status update:

- `PR-26A` has now been implemented as a backend-only slice and is tracked separately in `P4_PR26A_FILE_PLAN_RENAME_MOVE_FOUNDATION_DESIGN_20260414.md`
- `PR-26B` has now been implemented as a backend-only slice and is tracked separately in `P4_PR26B_RECORD_CATEGORY_RENAME_MOVE_FOUNDATION_DESIGN_20260414.md`

## Recommendation

Do not implement `PR-26` as a single mixed PR.

Split it into:

- `PR-26A`: file plan rename / re-parent foundation
- `PR-26B`: record category rename / re-parent with declared-record metadata repair

The reason is simple:

- file plan rename / move is primarily a subtree path + scope consistency problem
- record category rename / move is primarily a category-path + declared-record metadata + reindex problem

They share admin UX shape, but not repository risk shape.

## Current Blocking Risks

### File Plan Rename Is Unsafe Today

`FolderService.updateFolder(...)` can rename a folder, but it only saves the folder itself. It does not recursively refresh descendant paths.

By contrast, `NodeService.moveNode(...)` explicitly calls subtree path refresh.

That means file-plan rename currently reopens:

- descendant `path` drift in the database
- stale browse/navigation paths
- stale RM scope checks that rely on `path`
- stale summary breakdowns keyed by file-plan `path`
- stale search index paths unless a subtree reindex also happens

### File Plan Re-parent Is Not Just Generic Move

RM file plans already have domain placement rules:

- root
- workspace/system root
- another file plan

Generic node move semantics are broader than RM file-plan semantics. So RM re-parent must not just call the generic controller path without extra parent validation.

### Record Category Rename / Re-parent Is Unsafe Today

`Category` recalculates its own `path`, but there is no recursive child-category path repair chain for RM use.

More importantly, declared records duplicate category state into node properties:

- `rm:recordCategoryId`
- `rm:recordCategoryName`
- `rm:recordCategoryPath`

So category rename / move is not just a tree edit. It also requires:

- recursive category path repair
- declared-record metadata backfill
- node reindex for affected declared records

### Search / Facet Drift Exists For Category Rename

Search indexing stores node category names in `NodeDocument.categories`.

That means changing a category name without touching assigned nodes leaves search facets stale until the affected nodes are reindexed.

## PR-26A File Plan Rename / Re-parent

### Scope

`PR-26A` was implemented to cover:

- rename file plan
- re-parent file plan to another allowed RM parent
- subtree DB path repair
- subtree search reindex
- RM summary / guard semantics staying correct after rename or move

`PR-26A` was intentionally kept out of:

- batch file-plan restructure
- cross-tenant file-plan moves
- UI drag-and-drop

### Backend Design

Add dedicated RM APIs instead of routing admins through generic folder mutation APIs:

- `PUT /api/v1/records/file-plans/{folderId}/rename`
- `PUT /api/v1/records/file-plans/{folderId}/move`

Required backend behavior:

1. validate RM parent rules before mutation
2. reuse subtree path refresh semantics for all descendants
3. publish subtree-aware reindex after successful mutation
4. emit RM audit events specific to rename and move

### Minimum Required Code Seams

- `RecordsManagementService`
- `RecordsManagementController`
- `FolderService`
- possibly a reusable subtree path refresh helper extracted from `NodeService.moveNode(...)`
- `SearchIndexService`
- `EcmEventListener` only if subtree reindex should move to event-driven handling

### Suggested Internal Shape

The current codebase already has subtree path refresh for move but not rename. The safer direction is:

- extract a reusable subtree refresh primitive
- use it from both generic move and future RM rename

That is preferable to hand-rolling a second recursive path repair path inside RM service code.

### File Plan Rename / Move Exit Conditions

- all descendants have correct persisted `path`
- RM file-plan scope checks still return the same logical result after rename / move
- RM summary breakdown reflects new paths
- browse/search surfaces stop showing old paths after reindex

## PR-26B Record Category Rename / Re-parent

### Scope

`PR-26B` was implemented to cover:

- rename record category
- re-parent record category within RM tree
- recursive child-category path repair
- declared-record property repair
- affected-node reindex

`PR-26B` was intentionally kept out of:

- delete with reassignment
- bulk taxonomy merges
- category tree drag-and-drop UI

### Backend Design

Add dedicated RM APIs:

- `PUT /api/v1/records/categories/{categoryId}/rename`
- `PUT /api/v1/records/categories/{categoryId}/move`

Required backend behavior:

1. reject RM root mutation
2. reject cycles
3. repair descendant category paths recursively
4. find all declared records assigned to the mutated RM category subtree
5. rewrite:
   - `rm:recordCategoryName`
   - `rm:recordCategoryPath`
   - keep `rm:recordCategoryId` stable for rename; update only when assignment truly changes
6. save affected nodes and publish reindex/update events
7. emit RM audit events specific to rename and move

### Minimum Required Code Seams

- `RecordsManagementService`
- `RecordsManagementController`
- `CategoryRepository`
- `NodeRepository`
- `SearchIndexService`
- `Category` path-repair helper or dedicated RM recursive updater

### Why Node Backfill Is Mandatory

Today record declaration reads category state from relation when present, but still falls back to the RM properties if relation state is absent or stale.

That means rename / move without property repair would create split-brain behavior:

- some surfaces show new category path
- some surfaces still show old `rm:recordCategoryPath`

### Why Reindex Is Mandatory

Search index stores category names on nodes. Category rename must therefore reindex affected nodes, or search facets and category-based results remain stale.

## API / UI Boundaries

The UI should not expose direct rename / move affordances until the backend guarantees consistency.

When implemented later:

- file plan UI should likely use explicit dialogs, not inline freeform editing
- record category UI should likely show impact text:
  - descendant categories affected
  - declared records affected

This is one of the few cases where impact preview is worth the extra complexity because the operation fans out beyond the edited row.

## Suggested Ordering

1. `PR-26A` file plan rename / re-parent foundation
2. verify subtree path + subtree reindex behavior on real hierarchies
3. `PR-26B` record category rename / re-parent with metadata repair

Do not do `PR-26B` first. File-plan path semantics already affect more repository seams and remain the more dangerous correctness gap.

## Outcome

`PR-26` should only move into implementation once Athena can prove:

- rename/move no longer produce subtree path drift
- record-category rename/move no longer produce metadata drift
- affected search documents are reindexed automatically

Until then, `PR-25` remains the correct product boundary.
