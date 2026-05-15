# Template Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries where
SPA HTML fallback or malformed JSON can be treated as successful DTO data.
`templateService` backs managed FreeMarker template CRUD and execution. Before
this slice, all list/read/mutation/execute responses trusted the body shape
directly.

The backend contract comes from `TemplateController` and `TemplateService`:

- `TemplateController` is mounted at both `/api/templates` and
  `/api/v1/templates`. The frontend `api` client already prefixes `/api/v1`,
  so the existing `/templates` frontend paths are correct.
- `GET /templates` returns `List<TemplateDefinitionDto>`.
- `GET /templates/{templateId}`, `POST /templates`, and
  `PUT /templates/{templateId}` return one `TemplateDefinitionDto`.
- `DELETE /templates/{templateId}` returns `204 No Content`.
- `POST /templates/execute` returns `TemplateExecutionResult`.

## Design

- Add exported `TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE` for recognizable
  malformed-response failures.
- Guard `TemplateDefinitionDto` with required string fields `id`, `name`,
  `templatePath`, `engine`, `content`, `createdBy`, and `createdDate`;
  required `tags` as `string[]`; required boolean `active`; and nullable
  `description` / `lastModifiedDate`.
- Guard `TemplateExecutionResult` with string `rendered`, nullable
  `templatePath`, boolean `storedTemplate`, finite numeric `outputLength`, and
  string `executedAt`.
- Guard `listTemplates`, `getTemplate`, `createTemplate`, `updateTemplate`, and
  `executeTemplate`.
- Keep `deleteTemplate` unchanged as a no-content endpoint.
- Preserve all endpoint paths and payloads.

## Files Changed

- `ecm-frontend/src/services/templateService.ts`
- `ecm-frontend/src/services/templateService.test.ts`
- `docs/TEMPLATE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/templateService.test.ts --watchAll=false
```

Result:

- 1 suite passed.
- 13 tests passed.
- Coverage includes template list/read/create/update response guards,
  execution-result guards for stored and inline templates, HTML fallback
  rejection, malformed body rejection, nullable backend fields, and
  no-content delete wiring.

### Full Frontend Gates

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

```bash
cd ecm-frontend
CI=true npm run build
```

Result: compiled successfully. CRA still reports the existing bundle-size
advisory, and Node emits the known `fs.F_OK` dependency deprecation warning;
neither failed the build.

### Mainline Integration

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/templateService.test.ts \
  src/services/permissionTemplateService.test.ts --watchAll=false
```

Result:

- 2 suites passed.
- 36 tests passed.

Mainline `npm run lint`, `CI=true npm run build`, and `git diff --check
HEAD~2..HEAD` also passed after cherry-picking the parallel
`permissionTemplateService` slice.

### Remote CI

Run: `25894778899`.

Head commit: `19ab50b docs(templates): record local service guard verification`.

Result: passed.

- Backend Verify: passed.
- Frontend Build & Test: passed.
- Phase C Security Verification: passed.
- Property Encryption Closeout Gate: passed.
- Frontend E2E Core Gate: passed.
- Acceptance Smoke (3 admin pages): passed.
- Phase 5 Mocked Regression Gate: passed.

## Residual Work

- This slice does not add new template product capability.
- `deleteTemplate` still trusts HTTP success/failure because the backend
  endpoint returns no response body.
- Other frontend services may still need equivalent response-shape guards.
