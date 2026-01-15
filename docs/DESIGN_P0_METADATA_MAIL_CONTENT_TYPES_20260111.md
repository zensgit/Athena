# P0 Design: Mail Automation UI, Bulk Metadata, Content Types

## Goals
- Provide an admin UI for mail accounts and rules (create/update/delete + manual fetch).
- Provide bulk metadata editing (tags, categories, correspondent) for multi-select nodes.
- Provide content type schema management and apply schemas to nodes with custom fields.

## Backend Updates
- Mail automation:
  - CRUD endpoints for mail accounts and rules; password never returned in responses.
  - Manual fetch endpoint to trigger account polling.
  - Ingestion assigns tag by ID after upload.
- Metadata:
  - Bulk metadata service to add tags, categories, and update correspondent.
  - New bulk metadata endpoint under `/api/v1/bulk/metadata`.
- Content types:
  - Update + delete endpoints for content type definitions.
  - `applyType` validates schema properties and uses `NodeService.updateNode` to enforce permissions and emit update events.

## Frontend Updates
- New service clients:
  - `mailAutomationService` for account/rule CRUD and fetch.
  - `contentTypeService` for content types and apply.
  - `bulkMetadataService` for batch metadata updates.
- New admin pages:
  - Mail Automation page with account/rule tables and edit dialogs.
  - Content Types page with schema editor for property definitions.
- Bulk metadata dialog launched from FileBrowser for multi-selection updates.
- Properties dialog now supports:
  - Content type selection.
  - Dynamic field editors based on the selected schema.
  - Apply action that validates required fields before save.
- Admin menu adds navigation to Mail Automation and Content Types.

## API Surface
- Mail automation:
  - `GET/POST/PUT/DELETE /api/v1/integration/mail/accounts`
  - `GET/POST/PUT/DELETE /api/v1/integration/mail/rules`
  - `POST /api/v1/integration/mail/fetch`
- Bulk metadata:
  - `POST /api/v1/bulk/metadata`
- Content types:
  - `GET/POST/PUT/DELETE /api/v1/types`
  - `POST /api/v1/types/nodes/{nodeId}/apply?type=...`

## UX Notes
- Bulk metadata adds tags/categories and updates/clears correspondent (no destructive removals).
- Content type apply uses schema validation; empty optional fields are omitted in the payload.
- Clearing content type metadata is not exposed in this flow.

## Risks / Follow-ups
- Mail automation fetch requires a configured IMAP server for verification.
- Content type values are sent as strings; backend validation handles type conversion.
