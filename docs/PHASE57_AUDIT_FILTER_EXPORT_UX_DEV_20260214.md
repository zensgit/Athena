# Phase 57 - Audit: Filtered Explorer + Export Presets UX (Admin Dashboard)

Date: 2026-02-14

## Goal

Make audit investigation and export operationally smooth (Alfresco-style ergonomics):

- Filter audit logs by `user`, `eventType`, `category`, `nodeId`, and time range.
- Persist filters in the URL (shareable deep links).
- Export CSV with stable, descriptive filenames (date range + filters summary).

## UX Summary

Location: Admin Dashboard (tab "System Dashboard") audit controls.

- Filters:
  - `User` (freeSolo autocomplete)
  - `Event Type` (freeSolo autocomplete; displays human label but stores event code)
  - `Category` (select)
  - `Node ID` (UUID)
  - `From` / `To` (custom range)
- Actions:
  - `Filter Logs` updates URL query params and fetches filtered results.
  - `Reset` clears filters and restores "recent" logs.
  - `Export CSV` uses presets or a custom range and downloads a CSV with a stable filename.

## Key Design Decisions

### 1) Shareable URL Filters

We persist audit filters under dedicated query keys to avoid collisions with existing search routing:

- `auditUser`
- `auditEventType`
- `auditCategory`
- `auditNodeId`
- `auditFrom`
- `auditTo`

Implementation: `syncAuditUrlFilters(...)` updates the current location with `navigate(..., { replace: true })`.

To avoid a fetch loop when we update the URL internally, a `suppressAuditUrlSyncRef` flag is used to ignore the next `location.search` effect run.

### 2) Event Type Normalization (Fix: label vs code mismatch)

MUI `Autocomplete` with `getOptionLabel()` can emit the *formatted label* (e.g., "Node Created") via `onInputChange`, which would then be sent to the API as `eventType=Node Created` and not match backend expectations.

We normalize event types to codes (e.g., `NODE_CREATED`) everywhere that matters:

- UI input handler: `onInputChange` stores `normalizeAuditEventType(value)` so the state stays code-based.
- URL sync: persist normalized `auditEventType`.
- Audit search request: send normalized `eventType`.
- Export request + export filename: use normalized `eventType`.
- URL hydration: normalize `auditEventType` from the URL before requesting filtered logs.

`normalizeAuditEventType(...)` behavior:

- If input matches a known code, keep it.
- Else canonicalize: `trim().toUpperCase().replace(/\\s+/g, '_')`
- If the canonical form matches a known code, use it.
- If input matches a displayed label for a known code, map back to that code.
- Otherwise, return the canonical form.

### 3) Stable Export Filenames

Export downloads now use `buildAuditExportFilename(...)` to generate:

`audit_logs_<dateLabel>_preset-<preset>_user-<user>_event-<eventType>_cat-<category>_node-<uuid8>.csv`

Segments are sanitized for filesystem safety and truncated to avoid excessive length.

## Code Touchpoints

- Admin dashboard audit UX + export:
  - `ecm-frontend/src/pages/AdminDashboard.tsx`
- Mocked E2E coverage:
  - `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`

## Notes / Out of Scope

- This slice does not change backend audit endpoint contracts.
- Integration E2E coverage (full Docker stack) can be added later; the mocked spec keeps UI verification unblocked when Docker is unavailable.

