# Phase 368ZO — Sites Minimal Backbone

> **Scope**: Establish a first-class collaboration site registry in Athena
> **Date**: 2026-04-01

---

## 1. Problem Statement

Athena already had site-adjacent person capabilities, but no actual site domain model:

- no `Site` entity
- no `SiteRepository`
- no `SiteService`
- no `/api/v1/sites` controller contract
- no schema table for sites

That left a clear parity gap versus Alfresco-level collaboration primitives.

## 2. What Was Added

### Domain and persistence

- `Site` entity with:
  - `siteId`
  - `title`
  - `description`
  - `visibility`
  - `status`
  - optional `rootFolder`
- `SiteRepository` with site-id lookup and active-site listing
- Liquibase migration `046-create-sites-table.xml`

### Service layer

`SiteService` now supports:

- create
- get by `siteId`
- list active or include archived
- update mutable fields
- soft-delete/archive

Normalization and guards included:

- lowercase normalized `siteId`
- site-id pattern validation
- duplicate site rejection
- optional root-folder binding with existence check

### Controller layer

`SiteController` exposes:

- `GET /api/v1/sites`
- `GET /api/v1/sites/{siteId}`
- `POST /api/v1/sites`
- `PUT /api/v1/sites/{siteId}`
- `DELETE /api/v1/sites/{siteId}`

Write operations are admin-gated via `@PreAuthorize("hasRole('ADMIN')")`.

## 3. Files Changed

| File | Change |
|---|---|
| `ecm-core/src/main/java/com/ecm/core/entity/Site.java` | New site aggregate |
| `ecm-core/src/main/java/com/ecm/core/repository/SiteRepository.java` | New repository |
| `ecm-core/src/main/java/com/ecm/core/service/SiteService.java` | New CRUD/archive service |
| `ecm-core/src/main/java/com/ecm/core/controller/SiteController.java` | New REST contract |
| `ecm-core/src/main/resources/db/changelog/changes/046-create-sites-table.xml` | New Liquibase migration |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | Included migration 046 |
| `ecm-core/src/test/java/com/ecm/core/service/SiteServiceTest.java` | Focused service tests |
| `ecm-core/src/test/java/com/ecm/core/controller/SiteControllerTest.java` | Focused controller tests |

## 4. Not In Scope

- No frontend site workspace yet
- No membership model yet
- No activity feed integration yet
- No site dashboard/page yet

This phase is intentionally the smallest credible backend slice.
