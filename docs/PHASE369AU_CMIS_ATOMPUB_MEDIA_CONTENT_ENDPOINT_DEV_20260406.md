# Phase 369AU: CMIS AtomPub Media/Content Endpoint

## Goal

Close the last obvious AtomPub gap by adding a minimal content/media endpoint that reuses the shared CMIS content/versioning backbone.

## Delivered

- Added `GET /api/cmis/atom/media` and `GET /api/v1/cmis/atom/media` in [CmisAtomPubController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/CmisAtomPubController.java) to stream document content from [CmisContentVersioningService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisContentVersioningService.java).
- Added `PUT /api/cmis/atom/media` and `PUT /api/v1/cmis/atom/media` for raw-byte content updates, translating the HTTP body into the existing `setContentStream` mutation contract.
- Reused the same exception mapping used elsewhere in AtomPub:
  - `400` for bad request
  - `403` for permission failures
  - `404` for missing nodes
  - `500` for content IO failures
- Extended [CmisAtomPubControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/CmisAtomPubControllerTest.java) with focused media read/write coverage.

## Scope Boundaries

- No new browser-binding endpoint was added.
- No AtomPub multipart/media-link-entry workflow was introduced.
- This phase intentionally keeps content update input minimal: raw request bytes plus query params.
