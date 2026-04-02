# Phase 331 - Alfresco People Favorites Relation Resources

## Goal

Close the next Alfresco parity gap on `people/{personId}/favorites` and `people/{personId}/favorite-sites` by making Athena's people relation resources writable instead of read-only.

## Scope

- add `GET single / POST / DELETE` for `people/{username}/favorites`
- add `GET single / POST / DELETE` for `people/{username}/favorite-sites`
- keep write access constrained to the current user or an admin-managed profile
- validate `favorite-sites` targets before persistence so document favorites cannot leak into the site relation

## Backend Design

### Favorites relation

Athena now exposes:

- `GET /api/v1/people/{username}/favorites/{nodeId}`
- `POST /api/v1/people/{username}/favorites`
- `DELETE /api/v1/people/{username}/favorites/{nodeId}`

The create/delete path reuses `FavoriteService`, but now through explicit user-targeted methods so people self-service writes do not accidentally use the caller's implicit favorite context.

### Favorite sites relation

Athena now exposes:

- `GET /api/v1/people/{username}/favorite-sites/{siteId}`
- `POST /api/v1/people/{username}/favorite-sites`
- `DELETE /api/v1/people/{username}/favorite-sites/{siteId}`

`favorite-sites` is still derived from folder favorites, but creation now validates the target node is a folder before saving.

## Files

- `ecm-core/src/main/java/com/ecm/core/service/FavoriteService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`

## Result

Athena now matches the more useful Alfresco relation shape for people favorites and favorite sites, while keeping workspace-folder validation explicit on the site-facing path.
