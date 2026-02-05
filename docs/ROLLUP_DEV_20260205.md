# Athena ECM Rollup Dev Notes (2026-02-05)

## Backend
- **Permission Template Versioning**
  - Entities/DTOs/Repo: `PermissionTemplateVersion`, `PermissionTemplateVersionDto`, `PermissionTemplateVersionDetailDto`, `PermissionTemplateVersionRepository`.
  - Service: `PermissionTemplateService` snapshots versions on save and exposes list/version lookup.
  - Controller: `PermissionTemplateController` adds history and version detail endpoints.
  - Liquibase: `028-add-permission-template-versions.xml` added to master changelog.

- **Search Explainability & Highlighting**
  - `SearchHighlightHelper` + `SearchResult` updates for highlight snippets.
  - `FullTextSearchService`, `FacetedSearchService` incorporate highlight and ACL filtering.

- **Mail Automation Reporting**
  - `MailReportingService` + controller enhancements.
  - Reporting query support via `ProcessedMailRepository` and controller test coverage.

## Frontend
- **Permission Templates**
  - `PermissionTemplatesPage.tsx`
    - History dialog + compare dialog.
    - Change summary chips and diff table.
    - Export CSV action for compare diff.
    - Compare testid: `permission-template-compare-<versionId>`.

- **Search Results / Preview Retry**
  - `SearchResults.tsx` shows preview queue status (attempts, next retry) and bulk retry button.
  - `nodeService.ts` adds preview queue status typing and queue actions.

- **Version History UI**
  - `VersionHistoryDialog.tsx` adds compare summary blocks.

- **Mail Automation UI**
  - `MailAutomationPage.tsx` and `mailAutomationService.ts` updated for reporting panels and diagnostics.

## E2E Coverage
- `e2e/permission-templates.spec.ts`
  - Apply template flow.
  - History compare dialog.
  - Export CSV download validation.
- `e2e/search-preview-status.spec.ts`
  - Preview status filters.
  - Retry messaging/visibility.
- Additional tests: mail automation, search highlights, PDF preview, RBAC, rule automation, webhook admin.

## Docs Added
- Phase documents for mail reporting, search explainability, permission template versioning, diff/compare/export, preview retry status, version compare summary.
- Rollup design/verification docs added on 2026-02-05.
