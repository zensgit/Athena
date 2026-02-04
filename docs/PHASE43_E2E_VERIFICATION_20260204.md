# Phase 43 - Full E2E Verification (2026-02-04)

## Command
```
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_SKIP_LOGIN=1 npx playwright test
```

## Result
- **29 passed** (4.3m)

## Coverage Highlights
- Browse ACL, PDF preview, search facets/view, permissions dialog
- Rule automation & scheduled rules
- Mail automation diagnostics
- RBAC smoke
- Security + antivirus EICAR
- Version history actions + share links
