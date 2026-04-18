# P4 PR-26 Rename-Move Semantics Acceptance

## Scope

This document defines umbrella acceptance for the split `PR-26` follow-up:

- `PR-26A` file plan rename / re-parent
- `PR-26B` record category rename / re-parent

Status:

- `PR-26A` is now implemented and verified separately in `P4_PR26A_FILE_PLAN_RENAME_MOVE_FOUNDATION_VERIFICATION_20260414.md`
- `PR-26B` is now implemented and verified separately in `P4_PR26B_RECORD_CATEGORY_RENAME_MOVE_FOUNDATION_VERIFICATION_20260414.md`

## PR-26A Acceptance

- admin can rename a file plan through RM API
- admin can move a file plan only to an allowed RM parent
- invalid RM parents are rejected before mutation
- rename updates persisted `path` for the full file-plan subtree
- move updates persisted `path` for the full file-plan subtree
- RM file-plan scope checks remain correct after rename
- RM file-plan scope checks remain correct after move
- RM summary breakdown reflects the new file-plan paths
- affected subtree nodes are reindexed so search does not keep stale paths
- browse surfaces stop showing the old subtree paths after mutation
- RM audit logs record file-plan rename and move events
- full backend regression remains green
- front-end regression remains green if UI entry points are added
- front-end production build succeeds if UI entry points are added

## PR-26B Acceptance

- admin can rename a non-root record category through RM API
- admin can move a non-root record category within the RM tree through RM API
- RM root category remains protected from rename and move
- cycle creation is rejected before mutation
- descendant record-category paths are repaired recursively after rename
- descendant record-category paths are repaired recursively after move
- declared records assigned to the renamed/moved category subtree get repaired:
  - `rm:recordCategoryName`
  - `rm:recordCategoryPath`
- `rm:recordCategoryId` remains stable for pure rename/move operations
- affected declared records are reindexed so search facets do not keep stale category names
- RM admin UI reflects renamed/moved categories without requiring manual refresh workarounds
- RM audit logs record record-category rename and move events
- full backend regression remains green
- front-end regression remains green if UI entry points are added
- front-end production build succeeds if UI entry points are added

## Required Non-Goals

The following are not required for `PR-26A` / `PR-26B`:

- delete with reassignment
- bulk taxonomy merge
- drag-and-drop category tree editing
- batch file-plan restructure workflows

## Gate

`PR-26A` and `PR-26B` should not be approved on UI behavior alone.

Approval requires:

- DB consistency verification
- affected subtree / affected-node reindex verification
- explicit regression coverage for stale-path and stale-metadata prevention
