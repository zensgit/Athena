# Phase367ZZL - Node Controller Rendition Summary Source Of Truth

## Goal

Make ordinary node payloads prefer `RenditionResourceService` summary semantics instead of relying only on `Document.preview*`-backed inference.

## Why

Athena had already moved:

- node relations summary
- rendition relation endpoints
- search preview projection

toward rendition-backed semantics.

But the ordinary node APIs still returned `NodeDto.from(node)`, which meant:

- `GET /api/v1/nodes/{id}`
- `GET /api/v1/nodes/path`
- `GET /api/v1/nodes/{id}/children`
- `GET /api/v1/nodes/search`

were still primarily shaped by static helper inference from the document entity.

That left browse/detail flows one step behind the newer rendition summary contract.

## Design

### 1. Add a lightweight preview override on `NodeDto`

File:

- `ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java`

Add `withPreviewSemantics(...)` so callers can reuse all existing node fields while overriding:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

without rebuilding the entire DTO ad hoc inside controllers.

### 2. Route ordinary node responses through rendition summary

File:

- `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`

Add a private `toNodeDto(Node node)` helper:

- build the baseline `NodeDto`
- for document nodes, call `renditionResourceService.summarizeDocument(document)`
- if the summary is document-backed, overwrite preview semantics from the rendition summary
- otherwise fall back to the baseline DTO

Use this helper for:

- `getNode`
- `getNodeByPath`
- `getChildren`
- `searchNodes`

## Result

After this phase, ordinary node payloads start consuming the same rendition-backed preview semantics that Athena already uses in relation and rendition-specific APIs. This raises the source-of-truth ratio for browse/detail/search node flows without changing the public DTO shape.
