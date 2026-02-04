# Phase 39 - Full E2E (Prod Build) Verification (2026-02-04)

## Environment Notes
- Docker stack running with `ecm-core`, `ecm-frontend`, `elasticsearch`, `redis`, `postgres`, `keycloak`, `clamav`, `minio`.
- API health: `http://localhost:7700/actuator/health` returned 200 before the run.

## Command
```
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_SKIP_LOGIN=1 npx playwright test
```

## Result
- **29 passed** (4.4m)

## Scope Covered
- Browse ACL
- Mail automation diagnostics + actions
- PDF preview (client + server fallback)
- Search preview status
- Search view + similar results flow
- Permissions dialog
- Rule validation + scheduled rules
- RBAC smoke
- Security features + audit export + retention
- Antivirus EICAR flow
- Version details + download/restore + share links
