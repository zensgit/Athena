# Phase 2 Day 2 (P0) - Permission Template Diff Export JSON + Audit (Verification)

Date: 2026-02-12

## Environment

- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Keycloak: `http://localhost:8180`

Docker services (core + frontend) were rebuilt/recreated via:

```bash
bash scripts/restart-ecm.sh
```

## Backend Verification

### Unit tests (targeted)

```bash
cd ecm-core
mvn -q test -Dtest=PermissionTemplateServiceDiffTest,PermissionTemplateDiffExportControllerTest
```

Result: PASS

### API smoke (no secrets printed)

Create an admin token:

```bash
./scripts/get-token.sh admin admin
```

Fetch template versions (replace ids as needed):

```bash
TEMPLATE_ID=...   # UUID
FROM_ID=...       # UUID (older)
TO_ID=...         # UUID (newer)

curl -sS -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  "http://localhost:7700/api/v1/security/permission-templates/${TEMPLATE_ID}/versions/diff/export?from=${FROM_ID}&to=${TO_ID}&format=json" \
  | jq -r '{templateName, fromVersionNumber, toVersionNumber, added: (.added|length), removed: (.removed|length), changed: (.changed|length)}'
```

Expected: JSON includes keys `added`, `removed`, `changed`.

## Frontend Verification

### Playwright E2E (targeted)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/permission-templates.spec.ts -g "Admin can view permission template history"
```

Result: PASS

Assertions covered:
- Compare dialog shows `Export CSV` and `Export JSON`
- JSON download parses and contains `added`, `removed`, `changed` arrays

## Acceptance Check

- Admin can export permission template version diffs as both CSV and JSON.
- Export action logs a Security audit event (`SECURITY_PERMISSION_TEMPLATE_DIFF_EXPORT`).

