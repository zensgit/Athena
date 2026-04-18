# P4 PR-18 File Plan Record Category Foundation Design

## Goal

Add the next safe Records Management slice on top of `PR-16` and `PR-17`:

- introduce first-class file-plan folders
- introduce record-category hierarchy for declared records
- bind declared records to record categories
- tighten disposition so it only operates on declared records inside file plans

This PR is backend-first. It does not add browse-page file-plan management UI or record-category authoring UI.

## Design Choice

### Reuse the existing category model instead of adding a second RM tree table

Athena already has:

- `categories`
- `node_categories`
- category path/level semantics

So this slice extends the existing category aggregate with a purpose marker:

- `GENERAL`
- `RECORD`

This keeps record classification on the same storage primitives the repository already knows how to query and enforce, instead of introducing a parallel RM hierarchy model too early.

## Storage Model

### File Plans

File plans are modeled as a new folder subtype:

- `FolderType.FILE_PLAN`

That keeps file plans in the repository tree instead of a detached RM-only structure.

### Record Categories

Record categories reuse `Category` plus a new persisted purpose flag:

- `categories.purpose`

`081-record-category-foundation.xml` also seeds the root RM category:

- `/Records Management`

Everything under that subtree is treated as RM classification, not generic taxonomy.

### Declared Record Binding

Declared-record classification uses two layers:

- authoritative association: existing `node_categories`
- projected RM metadata on the record:
  - `rm:recordCategoryId`
  - `rm:recordCategoryName`
  - `rm:recordCategoryPath`

The association remains queryable through normal category relations, while the mirrored properties make RM state easy to surface through record DTOs without extra joins.

## Service Surface

`RecordsManagementService` now adds:

- `listFilePlans()`
- `createFilePlan(...)`
- `listRecordCategories()`
- `createRecordCategory(...)`
- `assignRecordCategory(nodeId, categoryId)`

Controller endpoints:

- `GET /api/v1/records/file-plans`
- `POST /api/v1/records/file-plans`
- `GET /api/v1/records/categories`
- `POST /api/v1/records/categories`
- `PUT /api/v1/nodes/{nodeId}/record/category`

All RM authoring remains admin-only in this slice.

## Enforcement

### 1. Generic category APIs cannot mutate RM classification

`CategoryService` now blocks:

- creating generic categories under RM record-category parents
- updating or moving record categories through generic category endpoints
- deleting record categories through generic category endpoints
- assigning/removing record categories through generic category APIs on non-record nodes

This prevents accidental bypass of RM semantics through the existing taxonomy surface.

### 2. Only declared records may carry record categories

If a category has purpose `RECORD`, assignment is only legal when:

- the target node is a declared record

Generic bulk metadata/category mutation paths inherit the same protection through `CategoryService`.

### 3. Disposition is limited to file plans and declared records

`DispositionScheduleService` now enforces:

- schedules can only be attached to `FILE_PLAN` folders
- execution candidates must be live `Document` nodes
- execution candidates must already be declared records

This aligns the destroy/archive pipeline with the RM control layer added in `PR-16`.

## Migration

`081-record-category-foundation.xml`:

- adds `categories.purpose`
- defaults existing rows to `GENERAL`
- seeds `/Records Management`
- seeds the three `rm:recordCategory*` property definitions

Rollback is intentionally narrow:

- remove seeded RM property definitions
- remove the seeded RM root only when unused
- drop `categories.purpose`

## Out Of Scope

- file-plan browse and authoring UI
- record-category tree UI
- undeclare/release workflow
- archive/import/transfer overwrite reconciliation
- RM reporting dashboards

## Key Files

- `ecm-core/src/main/resources/db/changelog/changes/081-record-category-foundation.xml`
- `ecm-core/src/main/java/com/ecm/core/entity/Folder.java`
- `ecm-core/src/main/java/com/ecm/core/model/Category.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/CategoryService.java`
- `ecm-core/src/main/java/com/ecm/core/service/DispositionScheduleService.java`
