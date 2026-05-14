# CMIS Service Shape Guards Design and Verification

## Context

Recent transfer-replication work closed one frontend service boundary that could
accept SPA HTML fallback as a successful API response. The CMIS Explorer has the
same class of risk: page tests mock `cmisService`, while the service itself used
typed `api.get<T>` calls and trusted whatever `/cmis/browser` returned.

If a mocked route or deployed reverse-proxy fallback returns `index.html` with
HTTP 200, the CMIS page can receive a string instead of repository, type, or
query DTOs. This slice makes the CMIS service fail closed with a clear error
before the page attempts to render malformed data.

## Design

- Add a shared `CMIS_UNEXPECTED_RESPONSE_MESSAGE` for malformed CMIS browser
  responses.
- Guard `getRepositoryInfo()` with required scalar fields and a string-array
  `capabilities` check.
- Guard `getTypeChildren()` with response envelope checks plus per-type
  validation for scalar fields and `propertyIds`.
- Guard `query(...)` with query envelope checks and require each result row to
  be an object.
- Keep the checks structural rather than enum-exhaustive. CMIS type IDs,
  capability names, and query row property keys are intentionally extensible.
- Leave the page behavior unchanged: existing page `catch` paths still show
  `Failed to load repository info`, `Failed to load type definitions`, or
  `Query execution failed`.

## Files Changed

- `ecm-frontend/src/services/cmisService.ts`
- `ecm-frontend/src/services/cmisService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/cmisService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 8 tests passed
- New coverage rejects HTML fallback for repository-info and query responses;
  rejects malformed repository capabilities, type definitions, and query rows;
  accepts guarded repository, type, and query responses.

### Targeted Service and Page Tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/cmisService.test.ts \
  src/pages/CmisExplorerPage.test.tsx \
  --watchAll=false
```

Result:

- 2 suites passed
- 13 tests passed
- Confirms the new service guards remain compatible with the existing CMIS
  Explorer page contract.

### Frontend Lint

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

### Production Build

```bash
cd ecm-frontend
CI=true npm run build
```

Result: compiled successfully. CRA still reports the existing bundle-size
advisory. Node emitted a dependency deprecation warning for `fs.F_OK`; it did
not fail the build.

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/services/cmisService.ts \
  ecm-frontend/src/services/cmisService.test.ts \
  docs/CMIS_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260513.md
```

Result: passed.

### Remote CI

Run: `25834365912`

Commit: `c86984b fix(cmis): guard browser service responses`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Property Encryption Closeout Gate: passed
- Frontend E2E Core Gate: passed
- Acceptance Smoke (3 admin pages): passed
- Phase 5 Mocked Regression Gate: passed.

## Residual Work

- This does not add new CMIS browser capabilities or backend protocol tests.
- Other frontend services may still need similar shape guards; this slice only
  covers the CMIS Explorer service boundary.
