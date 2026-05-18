# Workflow Service Shape Guards Design and Verification - 2026-05-18

## Scope

Implemented frontend-only response-shape guards for JSON-returning workflow service methods in `ecm-frontend/src/services/workflowService.ts`.

Out of scope and intentionally unchanged:

- Endpoint paths, query parameter names, and request payload shapes.
- Blob methods `getDefinitionDiagram` and `getProcessDiagram`.
- Void/write-only methods that do not consume JSON response bodies.
- Backend files, package files, and environment files.

## Controller Mapping Inspection

`WorkflowController` is rooted at `/api/v1/workflows`. The shared frontend API client already prefixes `/api/v1`, so `workflowService` continues to call paths rooted at `/workflows`.

Relevant controller mappings inspected:

- Definitions: `GET /definitions`, `GET /definitions/{definitionId}`, `GET /definitions/{definitionId}/model`, `GET /definitions/{definitionId}/start-form-model`.
- Diagrams: `GET /definitions/{definitionId}/diagram`, `GET /processes/{processId}/diagram`.
- Process starts and browser: `POST /document/{documentId}/approval`, `POST /document/{documentId}/approval/form-submit`, `POST /processes`, `GET /processes/browser`.
- Tasks and process resources: inbox, detail, process tasks/history/activities, variables, items, candidates, involved actors, and document history.

## Design

Added exported sentinel:

```ts
export const WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE =
  'Workflow endpoint returned an unexpected response. Backend route may be missing or the request may have received an HTML fallback.';
```

Guard approach:

- Validate JSON response containers before returning data to callers.
- Validate stable discriminator fields per DTO, such as IDs, booleans, paging fields, arrays, and option/value fields.
- Allow nullable/optional display fields where the backend DTOs can legitimately return null.
- Throw `WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE` for HTML fallback strings, missing containers, malformed paging, malformed form options, and similar unexpected shapes.

Guard coverage:

- Array responses: definitions, task inbox/my tasks, process tasks, task history, activities, process/task variables, process/task items, task candidates, involved actors, start/task form models, and document history.
- Object responses: start approval, start-form submit, start process, task detail, process detail, process browser page, definition detail, and definition model.
- Unchanged by design: `getDefinitionDiagram`, `getProcessDiagram`, `completeTask`, `submitTaskForm`, `claimTask`, `unclaimTask`, `assignTask`, `transitionTask`, `setProcessVariable`, `deleteProcessVariable`, `deleteProcess`, and `cancelProcess`.

## Tests

Added `ecm-frontend/src/services/workflowService.test.ts`.

Test coverage:

- Success path for definitions with endpoint preservation.
- Success path for approval start with payload preservation.
- Task inbox query normalization and endpoint/params preservation.
- Process browser params and guarded paging.
- Start form model guard success.
- Diagram blob methods still call `api.getBlob` with the original paths.
- HTML fallback rejection for a JSON endpoint.
- Malformed process browser paging rejection.
- Malformed form model option rejection.

## Verification

Initial setup:

```sh
npm ci
```

Result: passed. Installed missing frontend dependencies because `react-scripts` was not present before verification. `npm ci` reported existing dependency audit findings: 41 vulnerabilities, but no package files were changed.

Targeted Jest:

```sh
CI=true npm test -- --runTestsByPath src/services/workflowService.test.ts --watchAll=false
```

Result: passed.

```text
PASS src/services/workflowService.test.ts
Test Suites: 1 passed, 1 total
Tests:       9 passed, 9 total
Snapshots:   0 total
```

Lint:

```sh
npm run lint
```

Result: passed.

```text
eslint src --ext .ts,.tsx
```

## Blockers

No code blockers remain.

Verification note: the first targeted Jest attempt failed before tests ran because `react-scripts` was missing from `node_modules`; running `npm ci` resolved the local dependency setup.
