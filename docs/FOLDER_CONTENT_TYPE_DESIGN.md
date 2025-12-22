# Folder Contents contentType - Design

Date: 2025-12-22

## Goal
Include document MIME type in folder listing responses so the UI can reliably decide preview behavior without guessing.

## Problem
`/api/v1/folders/{id}/contents` returns `NodeResponse` without `contentType`, so the UI sees PDFs as unknown and may hide or mis-route preview actions.

## Approach
Add a `contentType` field to `FolderController.NodeResponse` and populate it when the node is a `Document`.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/controller/FolderController.java`

## Risks / Rollback
- Low risk; additive field in a JSON response.
- Rollback by removing the `contentType` field if needed.
