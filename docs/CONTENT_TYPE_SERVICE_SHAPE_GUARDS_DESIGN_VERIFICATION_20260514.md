# Content Type Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries where
SPA HTML fallback or malformed JSON can be treated as successful DTO data.
`contentTypeService` backs the content type admin page and the properties
dialog type picker. Before this slice, list/read/create/update responses trusted
the response body shape directly.

The backend contract comes from `ContentTypeController` and `ContentType`:

- `ContentTypeController` is mounted at `/api/v1/types`.
- `GET /types` returns `List<ContentType>`.
- `GET /types/{name}`, `POST /types`, and `PUT /types/{name}` return one
  `ContentType`.
- `DELETE /types/{name}` returns `204 No Content`.
- `POST /types/nodes/{nodeId}/apply?type={typeName}` returns a `NodeDto`, but
  current frontend consumers only await success/failure.

## Design

- Add exported `CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE`.
- Guard `ContentTypeDefinition` with required string `id`, `name`, and
  `displayName`; nullable `description` and `parentType`; and required
  `properties` as an array of valid property definitions.
- Guard `ContentTypePropertyDefinition` with required string `name` and
  `title`; `type` constrained to the existing frontend property type union;
  required boolean `required` and `searchable`; nullable `defaultValue`,
  nullable `options` as `string[]`, and nullable `regex`.
- Guard `listTypes`, `getType`, `createType`, and `updateType`.
- Keep `deleteType` unchanged as a no-content endpoint.
- Keep `applyType` as an effect endpoint: it now awaits the request and ignores
  the backend `NodeDto` body because existing callers do not consume it.

## Files Changed

- `ecm-frontend/src/services/contentTypeService.ts`
- `ecm-frontend/src/services/contentTypeService.test.ts`
- `docs/CONTENT_TYPE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/contentTypeService.test.ts --watchAll=false
```

Result:

- 1 suite passed.
- 11 tests passed.
- Coverage includes list/get/create/update response guards, nullable backend
  fields, HTML fallback rejection, malformed property definition rejection,
  no-content delete wiring, and effect-only apply wiring.

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

Pending after integration with the parallel `localizedContentService` slice:

- combined service Jest run
- remote GitHub Actions

## Residual Work

- This slice does not add new content type product capability.
- `applyType` still trusts HTTP success/failure rather than validating the
  returned `NodeDto`, because current consumers treat it as an effect endpoint.
- Other frontend services may still need equivalent response-shape guards.
