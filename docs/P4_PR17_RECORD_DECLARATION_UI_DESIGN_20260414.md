# P4 PR-17 Record Declaration UI Design

## Scope

`PR-17` closes the first usable front-end slice on top of the `PR-16` records-management backend:

- admin-only record declaration dialog in document preview
- record badge in document preview
- record declaration alert/details in document preview
- best-effort record badge rendering in `FileList` when node payload already carries `rm:record`
- dedicated front-end records-management service

## Design Choices

### 1. Keep the primary UX in `DocumentPreview`

The least invasive and most reliable surface is [DocumentPreview.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx:1):

- it already loads full node details
- it already has admin role knowledge
- it already owns the main document action menu

This avoids adding declaration logic to multiple browse/detail surfaces before the UX is stable.

### 2. Use a dedicated service instead of extending `nodeService`

[recordsManagementService.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/services/recordsManagementService.ts:1) maps directly to the backend controller:

- `GET /api/v1/records`
- `GET /api/v1/nodes/{nodeId}/record`
- `PUT /api/v1/nodes/{nodeId}/record`

This keeps RM concerns separate from generic node CRUD.

### 3. Make declaration comment part of the first UI slice

[DeclareRecordDialog.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/records/DeclareRecordDialog.tsx:1) captures the optional declaration comment instead of forcing a later UX retrofit.

### 4. Keep browse badge best-effort for now

[FileList.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/browser/FileList.tsx:1) now renders a record badge if `Node.aspects` already includes `rm:record`.

This deliberately does **not** add folder-page hydration through `GET /records`, because that endpoint is admin-only and currently unbounded. The authoritative state remains the preview/details surface.

## Files

- [recordsManagementService.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/services/recordsManagementService.ts:1)
- [RecordStatusChip.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/records/RecordStatusChip.tsx:1)
- [DeclareRecordDialog.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/records/DeclareRecordDialog.tsx:1)
- [DocumentPreview.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx:1)
- [FileList.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/browser/FileList.tsx:1)
- [types/index.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/types/index.ts:1)

## Deferred

- folder-page admin hydration of record badges
- properties dialog RM actions
- undeclare/release UI
- record dashboards / reporting
