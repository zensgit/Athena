# PHASE1 P63 - Audit Logs Filter by Node + Deep Link

Date: 2026-02-09

## Goal

Allow admins to:

- Filter audit logs by a specific `nodeId` (folder/document UUID).
- Jump from a node's action menu directly to a pre-filtered Audit view ("deep link").

## Current Behavior (Before)

- Admin audit filtering supported: `username`, `eventType`, `category`, and an optional date range.
- No `nodeId` filter in the Audit search/export endpoints.
- No direct navigation from a node to its related audit events.

## Proposed / Implemented Behavior

### Backend

Add optional `nodeId` filtering to audit search, and keep export consistent:

- `GET /api/v1/analytics/audit/search`
  - new optional param: `nodeId=<uuid>`
- `GET /api/v1/analytics/audit/export`
  - new optional param: `nodeId=<uuid>`

Filtering applies as an additional predicate:

- when `nodeId` is present: `audit_log.node_id = nodeId`
- when absent: no change (returns existing behavior)

### Frontend

Admin Dashboard (`/admin`) "Recent System Activity" filter bar:

- Add `Node ID` input.
- Include `nodeId` in `/analytics/audit/search` query parameters.
- Include `nodeId` in `/analytics/audit/export` query parameters.
- Validate UUID shape client-side; show toast error for invalid values.

Deep link:

- If the Admin Dashboard URL contains `?auditNodeId=<uuid>`, the page:
  - switches to the Overview tab
  - fills the `Node ID` input
  - runs an audit search filtered by that node

Node context menu:

- In file/folder action menu (browse list), admins see **View Audit**
- Clicking it navigates to `/admin?auditNodeId=<node.id>`

## API / Data Flow

1. User clicks `View Audit` on a node.
2. UI navigates to `/admin?auditNodeId=<uuid>`.
3. `AdminDashboard` reads the query param, sets `auditFilterNodeId`, and fetches:
   - `GET /api/v1/analytics/audit/search?nodeId=<uuid>`

## Files Changed

Backend:

- `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`

Frontend:

- `ecm-frontend/src/pages/AdminDashboard.tsx`
- `ecm-frontend/src/components/browser/FileList.tsx`

E2E:

- `ecm-frontend/e2e/audit-node-filter.spec.ts`

