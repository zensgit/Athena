# PHASE367ZZAA Rendition Mutation Effective Summary Contract

## Goal
- Make rendition mutation endpoints return the same rendition-backed effective preview semantics already used by node/search/admin surfaces.
- Remove the semantic drift where `REQUEUE` reported `invalidated=true`.

## Scope
- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java`
- `ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RenditionResourceControllerTest.java`

## Design
- Extend `RenditionMutationResult` with `RenditionSummary previewSummary`.
- Populate `previewSummary` from `summarizeDocument(document)` after `requeue` and `invalidate`.
- Correct `REQUEUE.invalidated` to `false`.
- Expose `previewSummary` through `RenditionMutationResponse` so all registry consumers can read effective `previewStatus / previewFailureReason / previewFailureCategory / previewLastUpdated / currentVersionLabel`.

## Outcome
- Shared rendition mutation responses now carry a consistent rendition-backed preview outcome.
- Registry consumers no longer have to infer post-mutation preview state from raw queue status alone.
