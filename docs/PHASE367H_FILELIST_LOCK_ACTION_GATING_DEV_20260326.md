# Phase367H FileList Lock Action Gating

## Goal

Push Athena's lock UX beyond passive badges by gating high-risk write actions directly in the browse context menu when an item is locked by another user.

## Benchmark Intent

Compared with `alfresco-community-repo`, this slice improves operator clarity in the browse layer:

- lock state is already visible from `Phase367G`
- write actions now reflect that state before the user trips a backend conflict
- the lock owner is surfaced inline in the action tooltip instead of only through a later failure

## Scope

Frontend-only, no API contract changes.

- extend `fileLockBadgeUtils` with caller-relative action gating helpers
- disable `Annotate (PDF)` when the selected PDF is locked by another user
- disable `Edit Online` when the selected Office document is locked by another user
- disable `Move` when the selected node is locked by another user
- disable `Delete` when the selected node is locked by another user
- keep read-only actions such as `View`, `Download`, `Properties`, and `Version History` available

## Design

### Helper contract

Add two browse-level helpers in `ecm-frontend/src/utils/fileLockBadgeUtils.ts`:

- `isLockedByAnotherUser(node, currentUsername)`
- `getFileLockActionReason(node, actionLabel, currentUsername)`

Design choices:

- username comparison is normalized case-insensitively
- when `locked=true` but `lockedBy` is absent, write actions still stay guarded
- action copy stays specific, for example `Cannot delete this item while locked by bob`

### FileList integration

`ecm-frontend/src/components/browser/FileList.tsx` gains a tiny `renderContextMenuItem(...)` wrapper:

- keeps the existing menu layout unchanged
- applies `disabled`
- wraps disabled items in a tooltip-safe `span`
- reuses the helper-generated reason string

This keeps the slice low-conflict inside an already dirty worktree.

## Files

- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/utils/fileLockBadgeUtils.ts`
- `ecm-frontend/src/utils/fileLockBadgeUtils.test.ts`

## Risk

- browse payloads only expose `locked/lockedBy`, not full lock diagnostics; this slice intentionally avoids lock expiry math and only guards obvious foreign-lock cases
- actions outside `FileList` still need separate lock-aware consumption to achieve full UI parity
