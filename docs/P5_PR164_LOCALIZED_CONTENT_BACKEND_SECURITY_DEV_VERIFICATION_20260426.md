# P5 PR-164 Localized Content Backend Security - Development + Verification

## Date
2026-04-26

## Status
Implemented and locally verified as an independent backend/API foundation slice.

## Scope
This slice closes the unsafe backend portion of Gap #14 multilingual content:

- Add `localized_content` persistence and API foundation.
- Register Liquibase migration `088-create-localized-content.xml`.
- Enforce node-level authorization in `LocalizedContentService`.
- Add focused backend tests for service behavior, controller routing, security, and changelog parsing.

The frontend admin page and shared route/menu wiring are intentionally not included in this commit because the current working tree wires several unrelated frontend slices through `App.tsx` and `MainLayout.tsx`.

## Files In Scope
| File | Change |
| --- | --- |
| `ecm-core/src/main/resources/db/changelog/changes/088-create-localized-content.xml` | New table, constraints, index, rollback |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | Registers migration 088 |
| `ecm-core/src/main/java/com/ecm/core/entity/LocalizedContent.java` | New `BaseEntity` child tied to `Node` |
| `ecm-core/src/main/java/com/ecm/core/repository/LocalizedContentRepository.java` | Locale lookup/delete helpers |
| `ecm-core/src/main/java/com/ecm/core/service/LocalizedContentService.java` | List/upsert/delete/resolve with permission checks |
| `ecm-core/src/main/java/com/ecm/core/controller/LocalizedContentController.java` | Authenticated REST API |
| `ecm-core/src/test/java/com/ecm/core/service/LocalizedContentServiceTest.java` | Service security + fallback tests |
| `ecm-core/src/test/java/com/ecm/core/controller/LocalizedContentControllerTest.java` | Controller contract tests |
| `ecm-core/src/test/java/com/ecm/core/controller/LocalizedContentControllerSecurityTest.java` | Authentication gate tests |

## Security Design
The controller uses `@PreAuthorize("isAuthenticated()")`; authorization is enforced in the service against the target node:

- `listForNode` and `resolve` call `securityService.checkPermission(node, READ)`.
- `upsert` and `delete` call `securityService.checkPermission(node, WRITE)`.
- All public methods resolve nodes with `findByIdAndDeletedFalseAndArchiveStatus(nodeId, LIVE)`, so deleted/archived nodes are hidden as not found.
- Blank or null locale values are rejected before repository access.
- Null request bodies for upsert are rejected.

This avoids exposing direct `/api/v1/nodes/{nodeId}/localizations` access as a pure authentication-only endpoint.

## Migration Notes
Migration 088 creates `localized_content` with:

- `node_id` as a non-null FK to `nodes(id)` with `ON DELETE CASCADE`.
- Unique constraint on `(node_id, locale)`.
- Index on `node_id`.
- `created_date` default using `${now}` for existing changelog compatibility.
- `created_by` and `last_modified_by` as `varchar(255)` to match `BaseEntity`.

## Verification
Commands run from `/Users/chouhua/Downloads/Github/Athena` unless noted.

```bash
git diff --check
```

Result: passed.

```bash
xmllint --noout \
  ecm-core/src/main/resources/db/changelog/changes/088-create-localized-content.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml
```

Result: passed.

```bash
cd ecm-core
./mvnw -Dtest=LocalizedContentServiceTest,LocalizedContentControllerTest,LocalizedContentControllerSecurityTest,LiquibaseChangelogParseTest test
```

Result: passed.

Summary:

- Tests run: 14
- Failures: 0
- Errors: 0
- Skipped: 0

## Parallel Review Results
Read-only parallel review found separate frontend work that should remain split:

- LDAP admin UI: service/controller contract matches, but current `App.tsx` and `MainLayout.tsx` wire multiple unrelated uncommitted pages. LDAP should be committed as a small isolated frontend slice.
- Site Invitations UI: two real product issues remain before shipping the frontend slice: site managers are blocked by an admin-only route/entry, and invitation email/accept flow lacks a natural clickable accept URL. `CONTRIBUTOR` is also missing from the role selector.
- Disposition Schedules UI: review result was not available after the delegated agent became unavailable, so it was not included in this backend/API commit.

## Not Included
These current working-tree changes are intentionally left for later isolated slices:

- `.env`
- Shared frontend routing/navigation changes in `ecm-frontend/src/App.tsx` and `ecm-frontend/src/components/layout/MainLayout.tsx`
- `ecm-frontend/src/pages/SitesPage.tsx`
- LDAP, Disposition, Site Invitation, and Localized Content frontend pages/services/E2E specs
- Broader test coverage documentation that references those frontend slices

## Next Slices
Recommended order:

1. Localized Content frontend slice: isolate `/admin/localized-content` route/menu, service, page, and mock E2E.
2. LDAP admin UI slice: isolate `/admin/ldap` route/menu, service, page, and mock E2E.
3. Site Invitations frontend fix slice: manager access, accept URL/token fallback, `CONTRIBUTOR` role option.
4. Disposition Schedules UI slice: independent review, then route/menu/page/service/E2E.
