# Phase 1 P1 - Audit Category Toggles (Development)

Date: 2026-01-31

## Goal
Allow administrators to enable/disable audit logging by event family (NODE, VERSION, RULE, etc.) with persisted settings and UI controls.

## Backend
- Added `AuditCategory` enum and `AuditCategorySetting` entity.
- Persisted settings to `audit_category_setting` table (Liquibase change 024).
- `AuditService` now caches category enablement and checks it before writing audit logs.
- Config `ecm.audit.disabled-categories` still seeds defaults for missing rows.

### API
- `GET /api/v1/analytics/audit/categories` — list category toggles.
- `PUT /api/v1/analytics/audit/categories` — update toggles.

## Frontend
- Admin Dashboard shows audit category switches and updates settings via API.
- Loading state and optimistic update with rollback on failure.

## Files Touched
- `ecm-core/src/main/java/com/ecm/core/entity/AuditCategory.java`
- `ecm-core/src/main/java/com/ecm/core/entity/AuditCategorySetting.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditCategorySettingRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/AuditService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `ecm-core/src/main/resources/db/changelog/changes/024-add-audit-category-settings.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
- `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
- `ecm-frontend/src/pages/AdminDashboard.tsx`
