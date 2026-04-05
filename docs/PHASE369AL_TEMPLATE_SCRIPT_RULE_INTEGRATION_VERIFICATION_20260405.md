# Phase 369AL: Template/Script Integration Into Rules Verification

## Focused Verification

Backend:

```bash
cd ecm-core && mvn -q -Dtest=RuleEngineServiceTemplateScriptActionTest,RuleEngineServiceValidationTest,RuleEngineServiceFolderScopeTest,RuleControllerActionDefinitionsTest,RuleControllerActionDefinitionsSecurityTest test
```

Frontend:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/RulesPage.tsx
cd ecm-frontend && npm run -s build
```

Diff hygiene:

```bash
git diff --check
```

## Expected Outcomes

- `EXECUTE_SCRIPT` rule actions execute stored or inline GraalJS and write results to metadata
- `RENDER_TEMPLATE` rule actions render stored or inline templates and write output to metadata
- Rule folder dry-run treats script actions as processable
- Rule action definitions expose both script/template actions with parameter metadata
- Non-admin rule authors cannot create or update rules containing template/script actions
