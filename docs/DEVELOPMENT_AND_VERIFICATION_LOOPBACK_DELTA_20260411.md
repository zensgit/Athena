# Development And Verification - Loopback Delta Semantics

- Date: 2026-04-11
- Scope: replication loopback delta fix, quota ADR capture

## Development

### Implemented

1. Reworked `LoopbackTransferClient` so folder replication now follows the same shape as the HTTP path:
   - align or resolve the folder node first
   - recurse into child nodes when the folder is mapped or otherwise usable
   - apply conflict policy only to unmapped conflicts
2. Added mapped-node handling for loopback folders and documents:
   - mapped unchanged folder returns `UNCHANGED` and still walks descendants
   - mapped unchanged document returns `UNCHANGED` with the mapped target ID
   - mapped changed document updates the existing target in place and refreshes mapping state
3. Preserved the existing behavior for unmapped `SKIP` conflicts:
   - the conflicting node is mapped
   - recursion stops at that node for the current run
4. Recorded the unresolved quota design boundaries in ADR-002 instead of extending code on an inconsistent accounting model.

### Changed Files

- `ecm-core/src/main/java/com/ecm/core/service/transfer/LoopbackTransferClient.java`
- `ecm-core/src/test/java/com/ecm/core/service/transfer/LoopbackTransferClientTest.java`
- `docs/adr/ADR-002-tenant-quota-accounting-and-context-boundaries.md`

## Verification

### Passed

1. `mvn -q -DskipTests compile`
2. `mvn -q -Dtest=LoopbackTransferClientTest test`
3. `git diff --check`

### Focused Coverage Added

1. Mapped unchanged folder still recurses into changed descendants.
2. Unmapped `SKIP` conflict stops recursion at the conflicting folder node.
3. Mapped unchanged document returns `UNCHANGED` with a concrete target ID instead of falling back to a null-target watermark skip.

### Blocked By Unrelated Working-Tree Changes

Broader backend regression could not be completed from the current working tree because unrelated uncommitted CMIS changes already present in the repo break test compilation:

- `ecm-core/src/test/java/com/ecm/core/cmis/CmisInteropSmokePackTest.java`
- failure mode: `CmisBrowserController` constructor signature mismatch
- observed while running: `mvn -q -Dtest=TransferReplicationServiceTest test`

This blocker is outside the files changed for the loopback fix and was not modified in this batch.
