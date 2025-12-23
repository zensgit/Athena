# Node Content-Type Fallback Report

Date: 2025-12-23

## Goal
Provide a reliable `contentType` in node listings even when the document record has a null MIME type, by falling back to stored properties.

## Changes
- Node DTO now uses `properties.mimeType` or `properties.contentType` when `Document.mimeType` is absent.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java`

## Verification
- Command: `mvn -q -DskipTests compile` (via Docker Maven)
- Result: PASS

## Notes
- Keeps existing API shape; only enriches `contentType` values for documents with missing MIME data.
