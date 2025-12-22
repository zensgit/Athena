# Search Results View Fix Design

## Goal
Ensure "View" from search results opens document preview for file hits, even when search results have incomplete metadata.

## Changes
- Strengthen document detection logic in `SearchResults` by:
  - Checking file extensions in both `name` and `path`.
  - Using additional metadata hints from `node.properties` (`mimeType`, `contentType`, `fileSize`, `size`).

## Rationale
Search index responses can omit `mimeType` or `fileSize`. When that happens, a document could be mistaken as a folder. The updated heuristics avoid misrouting to `/browse/:id` and keep the preview flow for documents.

## Impact
- View button behavior becomes consistent for document search results.
- Folder entries are still handled as folders unless they include file-like metadata or path extension.
