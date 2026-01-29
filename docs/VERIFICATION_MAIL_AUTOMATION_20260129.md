# Mail Automation Verification (2026-01-29)

## Scope
- Mail Automation connection + fetch summary
- Folder list + helper text

## Environment
- UI: http://localhost:3000
- API: http://localhost:7700
- Keycloak: http://localhost:8180
- User: `admin`

## Command
```
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
ECM_E2E_USERNAME=admin \
ECM_E2E_PASSWORD=admin \
npx playwright test e2e/mail-automation.spec.ts
```

## Result
- ✅ 2 passed (28.4s)

## Notes
- MCP browser transport was unavailable, so verification used Playwright CLI.
