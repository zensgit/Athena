# Phase369AJ Template Engine Backbone Verification

## Backend

Focused tests:

```bash
cd ecm-core && mvn -q -Dtest=TemplateServiceTest,TemplateControllerTest test
```

Expected coverage:

- create template
- duplicate-path rejection
- stored template execution
- inline template execution
- admin-only guard
- controller list/create/get/execute contract

## Frontend

Lint:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/pages/TemplateEnginePage.tsx \
  src/services/templateService.ts \
  src/utils/templateUtils.ts \
  src/utils/templateUtils.test.ts \
  src/App.tsx \
  src/components/layout/MainLayout.tsx \
  src/components/layout/MainLayout.menu.test.tsx
```

Focused tests:

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand \
  src/utils/templateUtils.test.ts \
  src/components/layout/MainLayout.menu.test.tsx
```

Build:

```bash
cd ecm-frontend && npm run -s build
```

Diff hygiene:

```bash
git diff --check
```

## Manual sanity checks

1. Open `/admin/templates`.
2. Create a stored template with path like `mail/welcome.ftl`.
3. Execute it with JSON model input and confirm rendered output appears.
4. Execute the inline scratch template and confirm output updates.
5. Confirm the `Template Engine` admin menu item is visible for admin users and hidden for viewer-only users.
