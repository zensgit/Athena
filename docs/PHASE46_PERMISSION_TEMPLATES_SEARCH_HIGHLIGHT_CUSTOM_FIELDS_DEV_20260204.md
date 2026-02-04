# PHASE46_PERMISSION_TEMPLATES_SEARCH_HIGHLIGHT_CUSTOM_FIELDS_DEV_20260204

## Scope
- Add admin-managed permission templates and apply them from the permissions dialog.
- Enable search highlight snippets in backend search responses and surface them in UI.
- Extend custom content type property types + validation.

## Backend changes
- Added `PermissionTemplate` entity with JSONB entries and CRUD + apply endpoints.
- Added Liquibase changelog `027-add-permission-templates.xml` and included in master.
- Added highlight query support in `FullTextSearchService` and `FacetedSearchService` with `<em>` tags.
- Extended `ContentTypeService` validation for `integer`, `float`, `monetary`, `url`, `documentlink`, `long_text`.

## Frontend changes
- New admin page `PermissionTemplatesPage` with CRUD UI and authority autocomplete.
- New API service `permissionTemplateService`.
- Permissions dialog now supports selecting + applying templates with optional replace.
- Admin menu and routes updated to include Permission Templates.
- Search results and advanced search highlight fallback include description/content/textContent/extractedText/title/name.
- Content types UI supports new property types and renders proper input controls in properties dialog.

## Notes
- Template apply uses `SecurityService.applyPermissionSet` per entry and respects replace flag.
- Highlight implementation uses Spring Data Elasticsearch highlight query package.
