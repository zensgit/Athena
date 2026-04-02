# Phase368C FileList Typed Lock Operator Surface

## Goal

Turn the newly added typed/deep lock backend into a real browse-level operator surface instead of leaving it as backend-only capability.

## Problem

Claude's lock enhancement added:

- `POST /nodes/{id}/lock-typed`
- `POST /nodes/{id}/unlock-deep`
- `POST /nodes/batch-lock`
- `POST /nodes/batch-unlock`

But the frontend still had no way to invoke typed lock or deep unlock. That meant Athena still lagged in actual operator usability even though the backend parity work existed locally.

## Implementation

### Frontend service contract

Updated `ecm-frontend/src/services/nodeService.ts`:

- Added `LockNodeTypedRequest`.
- Added `lockNodeTyped(nodeId, request)`.
- Added `unlockNode(nodeId)`.
- Added `unlockNodeDeep(nodeId, unlockChildren)`.

This keeps the new lock endpoints out of component-level URL assembly.

### FileList operator surface

Updated `ecm-frontend/src/components/browser/FileList.tsx`:

- Added `Lock...` context-menu action for unlocked nodes.
- Added `Unlock` context-menu action for locked nodes.
- Added `Unlock Deep` context-menu action for locked folders.
- Added a typed lock dialog with:
  - `lockType`
  - `lifetime`
  - ephemeral duration
  - `deep`
  - `additionalInfo`
- Added caller-aware unlock gating for owner/admin semantics.
- Refreshes the current folder after lock/unlock mutations.

### Focused controller coverage

Updated `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockTest.java` to pin the new backend endpoint shapes:

- typed lock request parameters
- deep unlock request parameter
- batch lock list payload
- batch unlock list payload

## Outcome

Athena now exposes typed/deep lock operations from the primary browse surface, so Claude's backend parity work becomes an actual daily-use operator feature rather than dormant API coverage.
