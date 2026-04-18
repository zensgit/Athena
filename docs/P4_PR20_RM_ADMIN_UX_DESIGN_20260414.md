# P4 PR-20 Records Management Admin UX Design

## Scope

`PR-20` closes the first dedicated records-management admin surface on the frontend.

Delivered scope:

- new admin route: `/admin/records-management`
- admin menu entry in the main layout
- summary dashboard backed by `GET /api/v1/records/summary`
- file-plan browse/create UI backed by `GET/POST /api/v1/records/file-plans`
- record-category browse/create UI backed by `GET/POST /api/v1/records/categories`
- declared-record browse UI backed by `GET /api/v1/records`
- record-category assignment UI backed by `PUT /api/v1/nodes/{nodeId}/record/category`
- audit table backed by `GET /api/v1/records/audit`

## Key Files

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/components/layout/MainLayout.tsx`
- `ecm-frontend/src/components/records/RecordStatusChip.tsx`

## Design Choices

### 1. Dedicated admin page instead of extending `AdminDashboard`

`AdminDashboard` is already broad and operationally dense. RM governance now has enough backend surface to justify its own page. This keeps RM state authoritative in one place and avoids another large panel inside the dashboard hub.

### 2. Backend-driven browse state

The UI does not infer file-plan or category state from generic folder/category trees. It uses the RM APIs directly:

- file plans come from `/records/file-plans`
- record categories come from `/records/categories`
- declared records come from `/records`
- audit timeline comes from `/records/audit`

This preserves the repository-level governance semantics added in `PR-18` and `PR-19`.

### 3. Category assignment from the admin page

`PR-17` let admins declare a record from preview. `PR-20` adds a follow-on classification seam so admins can assign record categories without going back to backend tools or raw API calls.

### 4. Minimal authoring, no speculative CRUD

The page only exposes operations that exist in the backend:

- create file plan
- create record category
- assign record category

It does not invent edit/delete/undeclare flows that are still deferred.

## Deferred

Still intentionally out of scope:

- file-plan tree browse inside the main browser
- undeclare/release workflow
- record-category rename/delete/move UI
- richer RM charts beyond summary buckets
- transfer/import-specific RM dashboards
