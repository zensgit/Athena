# P0A Ledger Fixup Design

Date: 2026-04-13

## Goal

Fix the correctness gaps left by `PR-1/PR-2` so `content_references` can behave as a real ownership ledger instead of a partial side table.

This fixup focuses on four concrete problems:

1. `DOCUMENT` ownership was backfilled once but not maintained at runtime.
2. orphan cleanup ignored the configured grace period.
3. backfill re-imported soft-deleted working copies as active owners.
4. fallback safety still ignored version-only references.

## Design Decisions

### 1. Keep ledger writes explicit in service/pipeline write paths

This fixup does not introduce a full repository policy pipeline yet.

Instead, it patches the concrete write paths that currently create, switch, copy, or permanently delete content ownership:

- `MetadataPersistenceProcessor`
- `InitialVersionProcessor`
- `VersionService`
- `CheckOutCheckInService`
- `NodeService`
- `PdfManipulationService`

The main helper is `ContentReferenceService.syncOwnerReference(...)`, which:

- detaches the old owner/content pair when content changes
- attaches the new owner/content pair
- re-attaches when content stays the same but the ledger row is missing

### 2. Make `DOCUMENT` owner tracking part of normal lifecycle

`DOCUMENT` ownership is now maintained when:

- a document is first persisted through the pipeline
- a document gets a new current version
- a version is promoted to current
- a working copy check-in overwrites the original document content
- a document node is created by normal node creation
- a document node is copied
- a document is permanently deleted

Permanent delete also detaches cascaded `VERSION` owners before the document row is removed.

### 3. Enforce orphan grace period in query selection

`orphan-cleanup.grace-hours` is now effective.

Cleanup candidates are selected only when:

- there are zero active references for a `content_id`
- the latest inactive reference timestamp (`updated_at` fallback `created_at`) is older than the configured cutoff

This keeps the grace-window decision in configuration while deriving orphan age from existing ledger timestamps.

### 4. Tighten migration semantics

`073-backfill-content-references.xml` is adjusted to:

- exclude soft-deleted working copies
- exclude soft-deleted versions
- remove the unsafe rollback blocks that deleted rows by `owner_type`

This migration is treated as data repair, not as a rollback-safe schema-only change.

### 5. Strengthen non-ledger fallback safety

`ContentService.isContentReferenced(...)` now falls back to:

- `documents`
- `versions`

This reduces the risk window if ledger data is incomplete or disabled.

## Files Changed

### Runtime

- `ecm-core/src/main/java/com/ecm/core/repository/ContentReferenceRepository.java`
- `ecm-core/src/main/java/com/ecm/core/repository/VersionRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/ContentReferenceService.java`
- `ecm-core/src/main/java/com/ecm/core/service/ContentService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/service/PdfManipulationService.java`
- `ecm-core/src/main/java/com/ecm/core/pipeline/processor/MetadataPersistenceProcessor.java`
- `ecm-core/src/main/java/com/ecm/core/pipeline/processor/InitialVersionProcessor.java`

### Migration

- `ecm-core/src/main/resources/db/changelog/changes/073-backfill-content-references.xml`

### Tests

- `ecm-core/src/test/java/com/ecm/core/service/ContentReferenceServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/CheckOutCheckInServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/VersionServiceLockSemanticsTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/NodeServiceContentReferenceTest.java`
- `ecm-core/src/test/java/com/ecm/core/pipeline/processor/MetadataPersistenceProcessorTest.java`
- `ecm-core/src/test/java/com/ecm/core/pipeline/processor/InitialVersionProcessorTest.java`

## Invariants After This Fixup

1. A document current binary should have a live `DOCUMENT` reference.
2. A saved version binary should have a live `VERSION` reference.
3. Permanent delete must detach document and version owners before row removal.
4. orphan cleanup must not delete binaries newer than the configured grace window.
5. historical soft-deleted working copies must not be backfilled as active owners.

## Deferred Items

This fixup intentionally does not solve these larger items:

- `check-in` creating versions automatically
- unified node lifecycle / policy pipeline
- the `DocumentController` legacy double-write upload flow
- incomplete Alfresco compatibility adapter semantics in `AlfrescoContentService`

Those should be handled in later P0A/P0B work, especially `PR-3`.
