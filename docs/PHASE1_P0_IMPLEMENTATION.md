# Phase 1 (P0) Implementation Notes

Date: 2026-01-30

## Scope
- Search: accurate totals + ES aggregations for facets/ranges + optional suggestions.
- Versioning: paged history + major-only view.
- Preview: persisted preview status + failure reason.
- Permissions: predefined permission sets (Coordinator/Editor/Contributor/Consumer).
- Audit: filtered query + export presets (time/user/event).
- Mail: email-to-node routing (nodeId@domain + folder alias).

## Changes by Area

### Search
- Full-text + advanced search now return ES total hits instead of page size.
- Faceted search switched to ES aggregations for facet counts and range buckets.
- Added optional suggestions (`includeSuggestions`) in faceted search response.
- Range aggregations added:
  - `fileSizeRange`: 0–1MB, 1–10MB, 10–100MB, 100MB+.
  - `createdDateRange`: last24h, last7d, last30d, last365d.

Key files:
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`

### Version Management
- Added major-only filter for version history.
- Added paginated version history endpoint.

Key files:
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/VersionRepository.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

API additions:
- `GET /api/v1/documents/{documentId}/versions?majorOnly=true`
- `GET /api/v1/documents/{documentId}/versions/paged?page=0&size=20&majorOnly=true`

### Preview / Rendition
- Persisted preview status + failure reason on `documents` table.
- Preview generation updates status to `PROCESSING`, then `READY` or `FAILED`.
- `PreviewResult` includes `status` and `failureReason`.

DB migration:
- `ecm-core/src/main/resources/db/changelog/changes/023-add-preview-status-columns.xml`

Key files:
- `ecm-core/src/main/java/com/ecm/core/entity/Document.java`
- `ecm-core/src/main/java/com/ecm/core/entity/PreviewStatus.java`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewResult.java`

### Permissions
- Added permission-set mapping for:
  - Coordinator, Editor, Contributor, Consumer.
- API endpoints for listing/applying sets.
- UI now exposes presets in the permissions dialog.

API additions:
- `GET /api/v1/security/permission-sets`
- `POST /api/v1/security/nodes/{nodeId}/permission-sets`

Key files:
- `ecm-core/src/main/java/com/ecm/core/entity/PermissionSet.java`
- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SecurityController.java`
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
- `ecm-frontend/src/services/nodeService.ts`

### Audit
- Filtered audit search by username/eventType/time.
- Export presets for time + user/event presets.
- UI supports filter + preset export controls.
- Audit export now uses refreshed Keycloak token from `authService` for UI fetch calls.

API additions:
- `GET /api/v1/analytics/audit/search`
- `GET /api/v1/analytics/audit/presets`
- `GET /api/v1/analytics/audit/export?preset=last7d`
- `GET /api/v1/analytics/audit/export?preset=user&username=...&days=30`
- `GET /api/v1/analytics/audit/export?preset=event&eventType=...&days=30`

Key files:
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `ecm-frontend/src/pages/AdminDashboard.tsx`

### Mail Automation
- Email-to-node routing:
  - Detects UUID in recipient local-part (e.g., `nodeId@domain`).
  - Supports folder aliases via node property (default `mailAlias`).
  - Optionally restricts routing to allowed domains.
- Route metadata stored in document properties: `mail:routeTargetId`, `mail:routeReason`, `mail:routeAddress`.

Config:
- `ecm.mail.routing.alias-property` (default `mailAlias`)
- `ecm.mail.routing.allowed-domain` (optional, comma-separated)

Key files:
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
