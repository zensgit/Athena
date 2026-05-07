# Preview Queue Stale Job Search Index Race Fix

Date: 2026-05-07

## Context

After the Aliyun / Tencent / 263 mail provider preset integration, GitHub Actions run `25503393913` recovered the previously failing `Phase 5 Mocked Regression Gate`, but `Frontend E2E Core Gate` failed in the preview/search regression subset.

The failing assertion was not in the mail preset surface. The regression gate created 12 unsupported `.bin` files, manually verified each document preview as `UNSUPPORTED`, then expected the advanced-search `previewStatus` facet to report `UNSUPPORTED=12`.

Observed CI failure:

```text
Advanced search preview facet did not reach UNSUPPORTED=12
total=12 READY=0, PROCESSING=8, QUEUED=0, FAILED=0, UNSUPPORTED=4, PENDING=0
```

The companion quick-search test was flaky for the same reason: a search result could still expose the transient `PROCESSING` state when the UI expected `Preview unsupported`.

## Root Cause

Document upload publishes an asynchronous `VersionCreatedEvent`. That listener enqueues preview generation after commit.

The E2E test also calls `GET /api/v1/documents/{id}/preview` directly. For generic binary files, that direct call quickly resolves the document to terminal `UNSUPPORTED` and re-indexes the document.

The race was:

1. Upload enqueues a preview job while the document is still preview-pending.
2. The test's direct preview request marks the document `UNSUPPORTED`.
3. A stale non-forced queued job starts later and calls `PreviewService.generatePreview(...)`.
4. `generatePreview(...)` first marks the document `PROCESSING`, and `SearchIndexService.updateDocument(...)` publishes that transient status to Elasticsearch.
5. Under CI load, the facet wait can observe several documents in `PROCESSING` before the queued job finishes and restores `UNSUPPORTED`.

This is a product race, not only a test timing issue: a stale background queue task should not overwrite a terminal unsupported state that was already established by a foreground preview request.

## Design

The memory-backed preview queue now stores whether a queued job was requested with `force=true`.

Before executing a memory queue job, `PreviewQueueService` re-loads and re-evaluates the current document state. If the job is non-forced and the document is already satisfied, the job is skipped instead of calling `PreviewService.generatePreview(...)`.

Satisfied states:

| State | Skip rule |
| --- | --- |
| `UNSUPPORTED` | Skip non-forced stale jobs. This directly fixes the unsupported `.bin` preview/search race. |
| `READY` with up-to-date content hash | Skip non-forced stale jobs because the preview is already current. |

Forced jobs are intentionally not skipped. This preserves existing operator/admin behavior where a forced queue request can rebuild even an unsupported or previously terminal document.

Redis-backed queue behavior was not changed in this slice. The CI and local compose defaults use the memory backend (`ECM_PREVIEW_QUEUE_BACKEND=memory`), and the observed failure was in that path.

## Files Changed

| File | Change |
| --- | --- |
| `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java` | Added the memory-job `force` flag and pre-execution satisfied-state skip. |
| `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java` | Added regression coverage for stale non-forced unsupported jobs and forced-job preservation. |

## Verification

Backend targeted regression:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PreviewQueueServiceTest test
```

Result: passed. `PreviewQueueServiceTest` exits `0`, including the new stale-job regression tests.

Diff hygiene:

```bash
git diff --check
```

Result: passed.

Local full-stack E2E replay was blocked because Docker is not running in this workspace:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock;
check if the path is correct and if the daemon is running:
dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

## Expected CI Impact

The next GitHub Actions run should keep these already-green jobs green:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Acceptance Smoke

The relevant target is `Frontend E2E Core Gate`, specifically the preview/search regression subset containing `e2e/search-preview-status.spec.ts`.

The expected behavioral change is that unsupported binary documents no longer regress from indexed `UNSUPPORTED` to indexed `PROCESSING` because of stale non-forced preview queue jobs.
