# ML Service Shape Guards - Design and Verification

Date: 2026-05-14

## Context

This slice continues the frontend service response-shape guard closeout line.
`mlService` is used by the ML suggestions dialog and previously trusted typed
API responses directly. A routed SPA HTML fallback or malformed payload could be
treated as successful health, classification, or tag-suggestion data.

## Backend Contract Evidence

`MLController` is mounted at `/api/v1/ml`, so the existing frontend relative
paths are correct:

- `GET /ml/health`
- `POST /ml/classify/{documentId}`
- `POST /ml/classify`
- `GET /ml/suggest-tags/{documentId}`
- `POST /ml/suggest-tags`

Backend response shapes:

- Health returns a map with `available`, `modelLoaded`, `modelVersion`, and `status`.
- Classification returns `ClassificationResult` with `success`, optional nullable
  `suggestedCategory`, optional nullable `confidence`, optional nullable
  `alternatives`, and optional nullable `errorMessage`.
- Tag suggestions return `List<String>`.

## Design

`ecm-frontend/src/services/mlService.ts` now:

- Exports `ML_UNEXPECTED_RESPONSE_MESSAGE`.
- Calls `api.get<unknown>` / `api.post<unknown>` and validates responses before
  returning typed values.
- Rejects HTML fallback and malformed response bodies.
- Preserves existing method names, endpoint paths, payloads, and return types.

Guard rules:

- Health must be a plain object with boolean `available`, boolean
  `modelLoaded`, string `modelVersion`, and string `status`.
- Classification must be a plain object with boolean `success`.
- Classification optional fields accept string/number/list values, `null`, or
  omission according to the backend DTO.
- Each alternative category requires string `category` and finite numeric
  `confidence`.
- Tag suggestions must be arrays of strings.

## Files Changed

- `ecm-frontend/src/services/mlService.ts`
- `ecm-frontend/src/services/mlService.test.ts`

## Verification

Targeted frontend verification:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/mlService.test.ts --watchAll=false
```

Result will be recorded after integration verification.

Result: PASS. `mlService.test.ts` ran 12 tests, 0 failures.

Full frontend gates:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result will be recorded after integration verification.

Result: PASS. `npm run lint` completed cleanly. `CI=true npm run build`
completed cleanly with the existing CRA bundle-size advisory.

## Residual Risk

This is a client-side response-shape guard only. It does not change ML service
availability, model behavior, classification thresholds, or backend
authorization.
