# Phase 1 P58: Version Compare Text Diff (API + UI)

Date: 2026-02-08

## Context
Athena ECM already supports:
- Version history (paged + major-only)
- Revert/restore and version downloads
- Metadata comparison (size/mime/hash deltas shown in the UI)

However, users still need an ergonomic way to understand *what changed* between two versions without downloading both files and diffing locally. Alfresco-like ECM systems commonly provide version history + restore and (at least for text) a "compare" capability.

This phase adds an **on-demand, bounded, text-only diff** between two versions, surfaced in the Version History compare dialog.

## Goals
- Provide an API that compares two specific versions of the same document and optionally returns a small text diff.
- Keep runtime and response payload bounded (safe for UI use).
- UI: Add a "Load text diff" action in Version History compare view and render the diff in a readable way.

## Non-Goals
- Binary diffs (PDF, images, Office formats).
- Side-by-side diff UX, syntax highlighting, or patch application.
- Background precomputation of diffs.

## Design / Approach

### API: Compare Two Versions (Optional Text Diff)
New endpoint:
- `GET /api/v1/documents/{documentId}/versions/compare`

Parameters:
- `fromVersionId` (UUID, required)
- `toVersionId` (UUID, required)
- `includeTextDiff` (boolean, default `false`)
- `maxBytes` (int, default `200000`)
- `maxLines` (int, default `2000`)

Response:
- `VersionCompareResultDto`:
  - `from`: `VersionDto`
  - `to`: `VersionDto`
  - `metadataChanged`: boolean
  - `contentChanged`: boolean (hash comparison)
  - `sizeDifference`: `Long` (nullable)
  - `textDiff`: `TextDiffDto` (nullable, only when requested)

Text diff response:
- `TextDiffDto`:
  - `available`: boolean
  - `truncated`: boolean
  - `reason`: string | null (filled when `available=false`)
  - `diff`: string | null (a small unified-like, line-based diff)

Key server-side safety limits (hard clamps):
- `MAX_COMPARE_TEXT_BYTES_HARD_LIMIT = 1_000_000`
- `MAX_COMPARE_TEXT_LINES_HARD_LIMIT = 10_000`
- `MAX_COMPARE_DIFF_CHARS_HARD_LIMIT = 400_000`

Behavior:
- Always validates both versions belong to the same document (and match `{documentId}`).
- Text diff is only available for:
  - `text/*`
  - `application/json`
  - `application/xml`, `text/xml`
  - `application/yaml`, `text/yaml`, `application/x-yaml`
- Text decoding uses UTF-8 (pragmatic default for the supported mime types).
- Diff algorithm is dependency-free and line-based (LCS-backed) intended for small inputs.
- If any limit is hit, `truncated=true` is returned, and the diff payload is clipped to `maxChars`.

### Backend Implementation Details
- `VersionService.compareVersionsDetailed(...)`:
  - Computes metadata/content change booleans and size delta
  - Optionally calls `buildTextDiff(...)` when `includeTextDiff=true`
- `buildTextDiff(...)`:
  - Reads at most `maxBytes` from each version content stream (detects truncation by reading `maxBytes+1`)
  - Uses `LineDiffUtils.diff(fromText,toText,maxLines,maxChars)` to build the output

Security:
- Underlying `getVersion(...)` path continues to enforce document permission checks via existing `VersionService` logic.
- Extra defensive check in controller rejects cross-document comparisons even if parameters are mismatched.

### UI: Version History Compare (Lazy Load)
The existing compare dialog (`VersionHistoryDialog`) is extended with:
- Section: **Content Diff (Text)**
- Button: **Load text diff** (only shown when both version mime types are text-like)
- Optional: **Download diff** (downloads as `version-diff-{from}-to-{to}.diff.txt`)
- Indicators:
  - `Truncated` chip when server indicates truncation
  - Reason string when diff is unavailable

API client:
- `nodeService.getVersionTextDiff(nodeId, fromVersionId, toVersionId, maxBytes?, maxLines?)`
  - Calls `/documents/{nodeId}/versions/compare` with `includeTextDiff=true`.

## Acceptance Criteria
- API returns a bounded diff for small text versions and rejects cross-document comparisons.
- UI can load diff on demand, renders it, and handles unavailable/truncated states.
- E2E covers the flow end-to-end: create two text versions, open compare, load diff, assert expected `- old` and `+ new` markers appear.

## Files Touched
- Backend:
  - `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
  - `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/TextDiffDto.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/VersionCompareResultDto.java`
  - `ecm-core/src/main/java/com/ecm/core/util/LineDiffUtils.java`
  - `ecm-core/src/test/java/com/ecm/core/util/LineDiffUtilsTest.java`
- Frontend:
  - `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
  - `ecm-frontend/src/services/nodeService.ts`
- E2E:
  - `ecm-frontend/e2e/version-share-download.spec.ts`

