# PR-21C RM Create-Path Guards Design

## Scope

`PR-21C` closes the remaining low-risk Records Management write seams that were still open after `PR-21A` and `PR-21B`:

- bulk import create paths
- transfer receiver create paths
- loopback replication create paths

This slice does not introduce `undeclare` or any new RM release workflow.

## Why This Slice

After `PR-21A` and `PR-21B`, Athena could already:

- detect RM-governed import/transfer activity
- expose RM operations telemetry
- block restore/archive/trash seams

But generic create paths could still write into RM-governed targets through:

- `BulkImportService`
- `TransferReceiverService`
- `LoopbackTransferClient`

That was still too loose for repository-level RM governance.

## Design Choices

### 1. Add a dedicated target-folder preflight in `RecordsManagementService`

New policy helper:

- `assertCreateInFolderAllowed(Node targetFolder, String operation)`

This blocks:

- file plan roots
- folders inside file plan scope

It intentionally does not expand into a global `DocumentUploadService` or `FolderService` rule, because that would widen the blast radius beyond the scoped RM hardening target.

### 2. Guard create paths at the service seams, not globally

The guard is applied only at the specific generic write seams that were still bypassing RM policy:

- `BulkImportService`
- `TransferReceiverService`
- `LoopbackTransferClient`

This keeps the change small and reviewable while still closing the live bypasses.

### 3. Guard both target-folder writes and implicit overwrite deletes

Two categories needed coverage:

- writing new content into a governed target folder
- deleting or mutating an existing governed node during `OVERWRITE`

So `PR-21C` uses:

- `assertCreateInFolderAllowed(...)` for target folder preflight
- `assertArchiveMutationAllowed(...)` for overwrite targets

## Backend Changes

### Records Management Policy

Extended:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`

Added:

- `assertCreateInFolderAllowed(...)`

### Bulk Import

Extended:

- `ecm-core/src/main/java/com/ecm/core/service/BulkImportService.java`

Changes:

- validate cached and resolved target folders before nested folder creation or document upload
- block overwrite of RM-governed existing nodes before `nodeService.deleteNode(...)`

### Transfer Receiver

Extended:

- `ecm-core/src/main/java/com/ecm/core/service/transfer/TransferReceiverService.java`

Changes:

- block folder/document replication into RM-governed target folders
- block mapped-node updates when the existing target is RM-governed
- defer delete-on-overwrite until after RM preflight passes

### Loopback Replication

Extended:

- `ecm-core/src/main/java/com/ecm/core/service/transfer/LoopbackTransferClient.java`

Changes:

- block loopback copy/version overwrite into RM-governed target folders
- block overwrite of RM-governed existing targets before delete/version-write

## Deferred

Still deferred after `PR-21C`:

- undeclare / release workflow
- RM-specific archive reopen semantics beyond existing guards
- richer RM dashboards beyond summary / audit / operations telemetry
