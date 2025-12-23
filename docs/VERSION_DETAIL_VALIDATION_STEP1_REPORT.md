# Version Detail Validation - Step 1 Report (2025-12-23)

## Scope
- Inventory current version metadata fields (backend + frontend).
- Define verification targets and gaps.

## Code Inventory
### Backend
- `ecm-core/src/main/java/com/ecm/core/dto/VersionDto.java`
  - Fields exposed: `id`, `documentId`, `versionLabel`, `comment`, `createdDate`, `creator`, `size`, `major`.
  - `versionLabel` falls back to `version.getVersionString()` when missing.
- `ecm-core/src/main/java/com/ecm/core/entity/Version.java`
  - Persistent fields include `versionNumber`, `versionLabel`, `majorVersion`, `minorVersion`,
    `contentId`, `mimeType`, `fileSize`, `contentHash`, `comment`, `changes`, `status`,
    `frozenDate`, `frozenBy`, plus BaseEntity audit fields.
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
  - `GET /api/v1/documents/{documentId}/versions` returns `VersionDto`.
  - `GET /api/v1/documents/{documentId}/versions/{versionId}/download` available.
  - `POST /api/v1/documents/{documentId}/versions/{versionId}/revert` available.

### Frontend
- `ecm-frontend/src/services/nodeService.ts`
  - `getVersionHistory()` maps API response to `Version` with fields:
    `id`, `documentId`, `versionLabel`, `comment`, `created`, `creator`, `size`, `isMajor`.
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
  - Table columns rendered: Version, Date, Created By, Size, Comment.
  - Actions: Download / Restore / Compare (disabled).

## Field Coverage vs. Entity
### Exposed Today (VersionDto / UI)
- `versionLabel`, `comment`, `createdDate`, `creator`, `size`, `major`

### Not Exposed Yet (Entity only)
- `mimeType`, `contentId`, `contentHash`, `status`, `changes`, `frozenDate`, `frozenBy`

## Verification Targets (Proposed)
**Required (MVP)**
- `versionLabel` increments after edit/check-in.
- `comment` persists for explicit check-ins.
- `createdDate` and `creator` match action user/time.
- `size` changes when file changes.
- `major` flag reflected for major check-ins.

**Optional / Extended**
- `mimeType`, `contentHash` if we add to DTO for integrity checks.
- `status`, `frozenDate`, `frozenBy` if release workflow added.
- `changes` (jsonb) for diff metadata (future).

## Functional Verification (Step 1)
Command (local API read):
```
GET /api/v1/documents/{documentId}/versions
```
Result sample (admin token):
- versions_count: 1
- keys: `id`, `documentId`, `versionLabel`, `comment`, `createdDate`, `creator`, `size`, `major`

## Gaps Identified
- API/DTO does not expose `mimeType`, `contentId`, `contentHash`, `status`, `changes`, `frozenDate`, `frozenBy`.
- UI does not surface any of these fields.

## Next Step
- Extend VersionDto + API mapping to include additional fields (at least `mimeType`, `contentHash`).
- Update UI dialog to display new fields or include in tooltip/details view.
