# Phase 369AL: Template/Script Integration Into Rules

## Goal

Connect the new template and script backbones to automation rules without rebuilding the rule authoring surface.

## Scope

- Add `RENDER_TEMPLATE` rule action alongside `EXECUTE_SCRIPT`
- Execute rule template/script actions through the existing automation engine
- Persist rendered/scripted output into document metadata via `outputProperty`
- Keep rule authoring safe by restricting template/script actions to admin-authored rules
- Surface the new action params in rule action definitions and the existing JSON-first rule editor

## Backend

- Extended [RuleAction.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/RuleAction.java) with:
  - `RENDER_TEMPLATE`
  - `scriptPath/script/templatePath/template/outputProperty/timeoutMs` param keys
- Added internal automation execution entry points in:
  - [TemplateService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TemplateService.java)
  - [ScriptService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ScriptService.java)
- Integrated both actions into [RuleEngineService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java)
  - build automation model from document state
  - persist result into `document.metadata[outputProperty]`
  - persist script logs into `document.metadata[outputProperty + "Logs"]`
  - remove `EXECUTE_SCRIPT` from dry-run unsupported actions
  - reject template/script rule authoring for non-admin users
- Updated [RuleController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RuleController.java) action definitions so rule-builder UIs can discover:
  - required params
  - optional params
  - `adminOnly` constraint
  - `atLeastOneOf:path,inline` style constraints

## Frontend

- Kept the existing JSON-first rule editor in [RulesPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/RulesPage.tsx)
- Improved action definition visibility by showing optional params as well as required params and constraints

## Notes

- This phase intentionally does not add a new visual rule-action builder. It makes the current rules UI capable of authoring template/script actions with accurate metadata first.
- Template/script actions are supported by the engine, but authoring them is deliberately admin-only.
