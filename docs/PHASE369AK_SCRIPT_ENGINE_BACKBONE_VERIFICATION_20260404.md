# Phase369AK Script Engine Backbone Verification

## Backend

Focused tests:

```bash
cd ecm-core && mvn -q -Dtest=ScriptServiceTest,ScriptControllerTest test
```

Expected coverage:

- create script
- stored script execution
- inline script execution
- blocked host access
- admin-only guard
- controller list/create/get/execute contract

## Frontend

Lint:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/pages/ScriptEnginePage.tsx \
  src/services/scriptService.ts \
  src/utils/scriptUtils.ts \
  src/utils/scriptUtils.test.ts \
  src/App.tsx \
  src/components/layout/MainLayout.tsx \
  src/components/layout/MainLayout.menu.test.tsx
```

Focused tests:

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand \
  src/utils/scriptUtils.test.ts \
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

1. Open `/admin/scripts`.
2. Create a stored script like `scripts/notify-site.js`.
3. Execute it with JSON model input and confirm result + log output appear.
4. Execute the inline scratch script and confirm both result and logs update.
5. Confirm `Script Engine` is visible to admin users and hidden for viewer-only users.
