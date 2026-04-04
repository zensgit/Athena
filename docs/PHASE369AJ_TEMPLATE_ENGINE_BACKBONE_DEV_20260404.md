# Phase369AJ Template Engine Backbone DEV

## Summary

This phase adds a minimal FreeMarker-backed template engine workspace for Athena admins:

- managed template persistence
- stored-template execution by `templatePath`
- inline template execution for scratch previews
- admin route and operator surface for create/update/delete/execute

The scope is intentionally limited to FreeMarker. GraalJS/script execution is still out of scope.

## Backend

### Domain and persistence

Added:

- [TemplateDefinition.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/TemplateDefinition.java)
- [TemplateDefinitionRepository.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/TemplateDefinitionRepository.java)
- [057-create-template-definitions-table.xml](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/resources/db/changelog/changes/057-create-template-definitions-table.xml)

`TemplateDefinition` stores:

- `name`
- `templatePath`
- `description`
- `engine`
- `content`
- `tags`
- `active`

The first backbone only supports `FREEMARKER`.

### Service

Added [TemplateService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TemplateService.java).

Core operations:

- `listTemplates()`
- `getTemplate(id)`
- `createTemplate(...)`
- `updateTemplate(...)`
- `deleteTemplate(id)`
- `executeTemplate(...)`

Execution supports two modes:

- stored template by `templatePath`
- inline `templateContent`

Both use a local FreeMarker `Configuration` with strict exception surfacing so render failures become explicit API errors rather than silent empty output.

Admin-only enforcement is handled inside the service to keep the new controller thin and consistent with other admin backbones in the repo.

### Controller

Added [TemplateController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/TemplateController.java).

Endpoints:

- `GET /api/v1/templates`
- `GET /api/v1/templates/{templateId}`
- `POST /api/v1/templates`
- `PUT /api/v1/templates/{templateId}`
- `DELETE /api/v1/templates/{templateId}`
- `POST /api/v1/templates/execute`

## Frontend

Added:

- [templateService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/templateService.ts)
- [TemplateEnginePage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TemplateEnginePage.tsx)
- [templateUtils.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/utils/templateUtils.ts)

Updated:

- [App.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/App.tsx)
- [MainLayout.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.tsx)
- [MainLayout.menu.test.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.menu.test.tsx)

The page provides:

- managed template list
- editor for stored templates
- JSON model input
- stored-template preview
- inline scratch template preview

Route:

- `/admin/templates`

Menu:

- `Template Engine`

## Notes

- This phase does not yet connect templates into rules/mail/workflow execution.
- This phase does not introduce server-side file-system template loaders.
- This phase does not add script execution; it is template-only by design.
