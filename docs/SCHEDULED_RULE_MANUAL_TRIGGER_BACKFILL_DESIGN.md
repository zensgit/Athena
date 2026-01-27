# Scheduled Rule Manual Trigger Backfill Design

## Problem
- Playwright regression intermittently failed in the scheduled rule manual trigger flow.
- The manual trigger sometimes found `0` candidate documents right after upload.

## Root Cause
- New scheduled rules have `nextRunAt = null`, so the background scheduler can run them immediately.
- That scheduler run can set `lastRunAt` *after* the test document upload.
- Manual trigger used `since = lastRunAt`, and candidate queries use `lastModifiedDate > :since`, so the just-uploaded document could be excluded.

## Decision
- Manual triggers should be resilient to recent scheduler runs.
- We add a small backfill window for manual triggers to include recently modified documents.

## Implementation
- Added config: `ecm.rules.scheduled.manual-backfill-minutes` (default `5`).
- Updated:
  - `ecm-core/src/main/java/com/ecm/core/service/ScheduledRuleRunner.java`
    - Manual trigger now uses:
      - `since = min(lastRunAt, now - manualBackfillMinutes)`
      - If `lastRunAt` is null, it uses the backfill window.
    - Scheduler path remains unchanged.

## Risk and Scope
- Scope is limited to manual trigger behavior.
- Manual triggers may reprocess a small recent window by design.
