# Phase367ZZO - Content Type Controller Rendition Convergence

## Goal

Remove the last remaining controller endpoint that still returned raw `NodeDto.from(...)` for a document mutation that can directly affect rendition applicability.

## Why

By this point Athena had already moved:

- ordinary node reads
- node mutations
- document mutations

onto rendition-backed preview semantics.

But `POST /api/v1/types/nodes/{nodeId}/apply` still returned a plain `NodeDto.from(...)` payload.

That was a real gap because applying a content type can change metadata and operator expectations around preview/rendition applicability, yet the response still lagged behind the rendition summary contract.

## Design

### 1. Add rendition-aware node mapping inside `ContentTypeController`

File:

- `ecm-core/src/main/java/com/ecm/core/controller/ContentTypeController.java`

Add `RenditionResourceService` as a controller dependency and introduce a private `toNodeDto(...)` helper:

- build baseline `NodeDto`
- if the node is a document, load `summarizeDocument(...)`
- if a document-backed summary exists, overwrite preview fields from the rendition summary

### 2. Route `applyType` through the helper

The `applyType(...)` endpoint now returns the same rendition-backed preview semantics used by other node/document surfaces instead of a raw entity-derived DTO.

## Result

After this phase, the last remaining controller-level `NodeDto.from(...)` mutation surface is gone. Content-type application now participates in the same rendition-backed preview contract as the rest of Athena's node/document APIs.
