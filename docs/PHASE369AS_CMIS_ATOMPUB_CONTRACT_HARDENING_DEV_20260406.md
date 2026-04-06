# Phase 369AS: CMIS AtomPub Contract Hardening

## Goal

Harden the AtomPub sidecar so its public contract matches actual behavior and reuses the same browser-side CMIS services for mutation/versioning semantics.

## Delivered

- Updated [CmisAtomPubController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/CmisAtomPubController.java) comments and OpenAPI tag to remove stale `read-only` wording.
- Standardized AtomPub error mapping for read and mutation endpoints:
  - `IllegalArgumentException -> 400`
  - `NoSuchElementException -> 404`
  - `SecurityException -> 403`
  - `IOException -> 500`
- Converged AtomPub working-copy operations onto [CmisContentVersioningService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisContentVersioningService.java) so AtomPub and browser binding share the same content/versioning abstraction.
- Added focused HTTP contract tests in [CmisAtomPubControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/CmisAtomPubControllerTest.java).
- Extended [CmisContentVersioningServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/cmis/CmisContentVersioningServiceTest.java) to cover AtomPub-specific working-copy flows.

## Scope Boundaries

- No new CMIS protocol surface was added.
- No browser-binding selectors/actions were changed.
- No AtomPub media/content endpoint was introduced in this phase.
