# Phase 368ZO — Sites Minimal Backbone — Verification

> **Date**: 2026-04-01

---

## 1. Verification Matrix

| # | Claim | Status |
|---|---|---|
| 1 | `Site` entity exists with site identity, visibility, status, and optional root folder | PASS |
| 2 | `SiteRepository` supports case-insensitive lookup and active listing | PASS |
| 3 | `SiteService` supports create/get/list/update/delete | PASS |
| 4 | Delete path is soft-delete/archive, not hard delete | PASS |
| 5 | `SiteController` exposes CRUD endpoints under `/api/v1/sites` | PASS |
| 6 | Liquibase master changelog includes migration `046-create-sites-table.xml` | PASS |
| 7 | Focused controller/service tests compile and pass | PASS |

## 2. Commands Run

```bash
cd ecm-core && mvn -q -Dtest=SiteServiceTest,SiteControllerTest test
```

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/entity/Site.java \
  ecm-core/src/main/java/com/ecm/core/repository/SiteRepository.java \
  ecm-core/src/main/java/com/ecm/core/service/SiteService.java \
  ecm-core/src/main/java/com/ecm/core/controller/SiteController.java \
  ecm-core/src/main/resources/db/changelog/changes/046-create-sites-table.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml \
  ecm-core/src/test/java/com/ecm/core/service/SiteServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/SiteControllerTest.java \
  docs/PHASE368ZO_SITES_MINIMAL_BACKBONE_DEV_20260401.md \
  docs/PHASE368ZO_SITES_MINIMAL_BACKBONE_VERIFICATION_20260401.md
```

## 3. Notes

- Verification for this phase is backend-only.
- No frontend site workspace was added in this slice.
- The initial controller test compile issue was repaired locally before the final passing run.
