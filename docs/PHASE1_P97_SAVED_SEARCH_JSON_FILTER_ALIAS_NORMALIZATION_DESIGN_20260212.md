# Phase 1 P97 Design: Saved Search JSON Filter + Alias Normalization

Date: 2026-02-12

## Background

- Existing saved-search compatibility already covered:
  - top-level legacy fields (`q`, `queryString`)
  - alias fields (`pathPrefix`, `createdFrom/createdTo`, `previewStatus`, `creators`)
- A remaining compatibility gap existed for imported payloads where:
  - `queryParams` itself may be JSON string
  - `filters` may be JSON string
  - aliases are mixed (`mimeType`, `creator`, `pathStartsWith`)
  - preview status uses non-canonical values (`unsupported_media_type`, `in_progress`, `error`)
  - booleans are encoded as `1/0` or `yes/no`

## Goal

Normalize mixed saved-search payload shapes into stable `SearchCriteria` so advanced dialog prefill is deterministic across import sources and historical payload versions.

## Scope

- Frontend parser only:
  - `ecm-frontend/src/utils/savedSearchUtils.ts`
- Regression:
  - unit tests in `ecm-frontend/src/utils/savedSearchUtils.test.ts`
  - Playwright saved-search load flow in `ecm-frontend/e2e/saved-search-load-prefill.spec.ts`

## Implementation

1. Added object parsing for string payloads
- New helper `asRecord(...)`:
  - accepts object
  - accepts JSON string and parses object
  - safely ignores invalid JSON
- Applied to:
  - `item.queryParams`
  - `queryParams.filters` / `queryParams.filter` / `queryParams.criteria`

2. Expanded alias mapping
- MIME aliases:
  - `mimeTypes` | `mimeType` | `mimetype`
- Creator aliases:
  - `createdByList` | `creators` | `creator` | `createdByUser`
- Path aliases:
  - `path` | `pathPrefix` | `pathStartsWith`
- Date-range object aliases:
  - `createdRange` / `createdDateRange` (`from`, `to`)
  - `modifiedRange` / `modifiedDateRange` (`from`, `to`)

3. Hardened value normalization
- `asBoolean` now supports:
  - boolean
  - `"true"/"false"`
  - `1/0` and `"1"/"0"`
  - `"yes"/"no"`
- `asString` now trims and drops empty strings.

4. Canonical preview status normalization
- Added known status allow-list:
  - `READY`, `PROCESSING`, `QUEUED`, `FAILED`, `UNSUPPORTED`, `PENDING`
- Added alias mapping:
  - `IN_PROGRESS`/`RUNNING` -> `PROCESSING`
  - `WAITING` -> `QUEUED`
  - `ERROR` -> `FAILED`
  - `UNSUPPORTED_MEDIA_TYPE`/`UNSUPPORTED_MIME`/`PREVIEW_UNSUPPORTED` -> `UNSUPPORTED`
- Added substring fallback:
  - any token containing `UNSUPPORTED` -> `UNSUPPORTED`
- Output deduplicated in stable order.

## Expected Outcome

- Loading saved searches from mixed import formats no longer drops name/type/creator/date/status criteria.
- Advanced Search prefill and criteria summary remain stable across legacy and JSON-string payload styles.
