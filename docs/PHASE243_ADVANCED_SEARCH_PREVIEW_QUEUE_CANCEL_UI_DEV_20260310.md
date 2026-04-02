# Phase 243 - Advanced Search Preview Queue Cancel UI + Governance Feedback (Dev)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Goals

1. Complete Stream B UI loop for queue governance by exposing cancel action on search result cards.
2. Surface backend queue governance semantics (`queueState`, queue message) directly in result-level preview diagnostics.
3. Add mocked e2e coverage for retry-then-cancel flow.

## 2. Frontend Implementation

## 2.1 Advanced Search queue-state model and cancel operation

File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

- Added UI queue status model:
  - `PreviewQueueUiStatus` (`queueState`, `message`, `attempts`, `nextAttemptAt`)
- Added active queue-state gate:
  - `QUEUED`, `RUNNING`, `PROCESSING`, `CANCEL_REQUESTED`
- Added result-level cancel handler:
  - calls `nodeService.cancelQueuedPreview`
  - writes returned `queueState/message` back into `previewQueueStatusById`
  - user feedback:
    - success toast when cancelled
    - info toast when no active task
    - error toast on request failure

## 2.2 Result card action bar extension

File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

- Added action button:
  - `aria-label="Cancel preview task"`
  - shown when queue task is active by queue-state gate
- Kept retry/force-rebuild actions and added disable coordination with cancel in-flight state.
- Queue tooltip/caption now includes:
  - queue state
  - queue message
  - attempts
  - next retry timestamp

## 2.3 Mocked e2e extension

File: `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`

- Added mocked endpoint support:
  - `POST /api/v1/documents/{id}/preview/queue/cancel`
- Added assertions in existing all-matched scenario:
  - run single-item retry (`Retry failed previews`)
  - confirm `Cancel preview task` button appears
  - click cancel and assert cancel API call count
  - assert UI reflects `Queue state: CANCELLED`

## 3. Design Notes

- UI cancel action is only exposed when local queue state indicates an active task, avoiding unnecessary idle cancellations.
- State is backend-authoritative after cancel response; UI no longer infers terminal state without server confirmation.
- This closes the UI observability gap introduced by backend `queueState` semantics in Phase 242.
