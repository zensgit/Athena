# Phase369AK Script Engine Backbone DEV

## Summary

This phase adds a minimal admin-only GraalJS script engine workspace:

- managed script persistence
- stored-script execution by `scriptPath`
- inline script execution for scratch previews
- host-restricted sandbox execution
- admin route and operator surface for create/update/delete/execute

The scope is intentionally limited to GraalJS. It does not yet integrate with rules, workflow actions, or file-system script deployment.

## Backend

### Domain and persistence

Added:

- [ScriptDefinition.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/ScriptDefinition.java)
- [ScriptDefinitionRepository.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/ScriptDefinitionRepository.java)
- [058-create-script-definitions-table.xml](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/resources/db/changelog/changes/058-create-script-definitions-table.xml)

Stored fields:

- `name`
- `scriptPath`
- `description`
- `engine`
- `content`
- `tags`
- `active`

The first backbone supports only `GRAALJS`.

### Service

Added [ScriptService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ScriptService.java).

Core operations:

- `listScripts()`
- `getScript(id)`
- `createScript(...)`
- `updateScript(...)`
- `deleteScript(id)`
- `executeScript(...)`

Execution supports two modes:

- stored script by `scriptPath`
- inline `scriptContent`

Sandbox posture in this phase:

- no host access
- no host class lookup
- no native access
- no thread creation
- no IO
- bounded execution timeout via executor + future timeout

Bindings exposed to guest JS:

- `model`
- top-level keys from `model`
- `logger`
- `console`
- `utils.now()`
- `utils.uuid()`
- `utils.stringify(value)`

### Controller

Added [ScriptController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/ScriptController.java).

Endpoints:

- `GET /api/v1/scripts`
- `GET /api/v1/scripts/{scriptId}`
- `POST /api/v1/scripts`
- `PUT /api/v1/scripts/{scriptId}`
- `DELETE /api/v1/scripts/{scriptId}`
- `POST /api/v1/scripts/execute`

All operations are admin-only through service-level enforcement.

## Frontend

Added:

- [scriptService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/scriptService.ts)
- [ScriptEnginePage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/ScriptEnginePage.tsx)
- [scriptUtils.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/utils/scriptUtils.ts)

Updated:

- [App.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/App.tsx)
- [MainLayout.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.tsx)
- [MainLayout.menu.test.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.menu.test.tsx)

The page provides:

- managed script list
- script editor
- JSON model input
- stored-script execution preview
- inline scratch script preview
- result rendering and captured logs

Route:

- `/admin/scripts`

Menu:

- `Script Engine`

## Notes

- This phase does not yet connect scripts into rule actions.
- This phase does not yet provide a filesystem-backed script registry.
- This phase does not yet provide tenant-aware script isolation.
