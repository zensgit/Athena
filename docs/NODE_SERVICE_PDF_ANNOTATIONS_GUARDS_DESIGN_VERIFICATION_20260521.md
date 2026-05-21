# nodeService PDF Annotation Shape Guards: Design and Verification

## Context

The node-service guard closeout identified `getPdfAnnotations` and
`savePdfAnnotations` as the next tiny JSON follow-up slice if the target became
"every JSON method". This slice covers those two methods while preserving their
existing endpoints, request body, and `DocumentPreview` error handling.

## Scope

In scope:

- `getPdfAnnotations`
- `savePdfAnnotations`

Out of scope:

- PDF preview rendering behavior.
- PDF download/blob behavior.
- Backend annotation persistence.
- Non-annotation `nodeService` methods.

## Design

The slice reuses the service-wide `NODE_UNEXPECTED_RESPONSE_MESSAGE` and the
existing node-service helper bundle. No new sentinel or response style was
introduced.

New wire guards:

- `isPdfAnnotationWire`
- `isPdfAnnotationStateWire`

New normalization helpers:

- `normalizePdfAnnotation`
- `assertAndNormalizePdfAnnotationState`

The backend annotation DTO allows nullable metadata fields. The frontend
contract expects `PdfAnnotation.text` to be a string and treats other annotation
metadata as optional. The guard therefore:

- Requires `annotations` to be an array.
- Requires each annotation `page`, `x`, and `y` to be finite numbers.
- Accepts nullable or missing `id`, `text`, `color`, `createdBy`, and
  `createdAt`.
- Normalizes nullable `text` to `''`.
- Normalizes nullable annotation metadata to `undefined`.
- Normalizes nullable state metadata to `null`.

Malformed JSON now fails before it reaches annotation marker state or save
state.

## Consumer Behavior

Existing `DocumentPreview` behavior is preserved:

- Load failures are caught and shown as `Failed to load annotations`.
- Save failures are caught and shown as `Failed to save annotations`.
- The `POST /documents/{id}/annotations` request body remains
  `{ annotations }`.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/nodeService.pdfAnnotations.test.ts --watchAll=false
```

Result:

- 1 suite passed.
- 4 tests passed.

Node-service regression sweep:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.pdfAnnotations.test.ts \
  src/services/nodeService.permissions.test.ts \
  src/services/nodeService.versionHistory.test.ts \
  src/services/nodeService.lockCheckout.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.folderNodeCrud.test.ts \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts \
  --watchAll=false
```

Result:

- 11 suites passed.
- 81 tests passed.

Additional checks:

- `npm run lint`: passed.
- `CI=true npm run build`: passed. CRA emitted only the existing bundle-size
  advisory and `fs.F_OK` deprecation warning.
- `git diff --check -- . ':!.env'`: clean.
