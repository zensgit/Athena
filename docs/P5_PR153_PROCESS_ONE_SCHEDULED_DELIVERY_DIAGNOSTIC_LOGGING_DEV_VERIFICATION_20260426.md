# P5 PR-153 — `processOneScheduledDelivery` Diagnostic Logging

## Date
2026-04-26

## Scope

Diagnostic-only backend slice. INFO-level logging in
`RmReportPresetDeliveryService.processOneScheduledDelivery` to make
the next CI run name the exact branch that produces
`processedCount: 0` for the notification acceptance gate.

No new endpoint, no test, no migration. ~16 lines added.

## Context — what landed first

PR-149's REQUIRES_NEW isolation worked. Three runs (`11809e3`,
`9b81041`, `b57ff31`) all showed the same shape:

| Job | Status |
|-----|--------|
| Backend Verify | ✅ |
| Frontend Build & Test | ✅ |
| Phase C Security | ✅ |
| Acceptance Smoke | ✅ |
| Frontend E2E Core Gate | ❌ at notification gate step |
| Phase 5 Mocked | still in progress |

The notification step's failure changed shape too:

```
Error: expect(runDuePayload.processedCount).toBe(1)
  Expected: 1
  Received: 0
```

So:

- `POST /api/v1/records/report-presets/run-scheduled-deliveries`
  returns **200 OK** (no more 500 — diagnostic chain fixed the
  rollback issue)
- Body has `{"processedCount": 0}` instead of expected 1
- The forced-due preset exists, but the per-preset processing path
  returns false (or throws and the loop's defence-in-depth catch
  swallows it)

## What I cannot see (and why diagnostic logs)

The artifact's `tmp/ci-e2e-logs/ecm-core.log` retains only **200
trailing lines** — and most of those are a stack trace from an
unrelated async handler (LazyInitializationException in
`EcmEventListener.handleNodeDeleted` during cleanup, pre-existing).
None of the lines name the inner-loop execution of
`processOneScheduledDelivery`.

Hypotheses for why `processedCount = 0`:

1. **Most likely**: `claimScheduledRun` returns false because the
   CAS query doesn't match. The test's `forcePresetNextRunAtPast`
   sets `next_run_at = now() - interval '1 minute'` via direct SQL
   (microsecond precision). The JPA-read entity may have a slightly
   different timestamp value due to LocalDateTime nanosecond
   handling, so the CAS condition `next_run_at = :expectedNextRunAt`
   doesn't match.
2. **Possible**: `deliverPreset` throws an exception, but its inner
   catch normally calls `persistFailedExecution` and returns a dto.
   If `persistFailedExecution` itself throws (e.g., on
   constraint violation when saving the FAILED execution row), it
   propagates. The loop's outer catch then swallows it.
3. **Less likely**: `findByScheduleEnabledTrue...` returns 0 due
   presets because of a transactional visibility gap between the
   test's force-via-SQL and the scheduler's query.

Without inner-loop logs, I cannot tell which.

## Design

Add INFO-level logs at every branch of `processOneScheduledDelivery`:

```java
log.info("processOneScheduledDelivery: enter presetId={} nextRunAt={} cron={}",
    preset.getId(), preset.getNextRunAt(), preset.getCronExpression());
if (!claimScheduledRun(preset)) {
    log.info("processOneScheduledDelivery: claim CAS lost for presetId={}", preset.getId());
    return false;
}
RmReportPreset claimedPreset = ...;
if (claimedPreset == null) {
    log.warn("RM report preset {} disappeared after claim; ...", preset.getId());
    return false;
}
SecurityContext previous = pushPresetAuthentication(claimedPreset);
try {
    log.info("processOneScheduledDelivery: calling deliverPreset for presetId={} folderId={}",
        claimedPreset.getId(), claimedPreset.getDeliveryFolderId());
    deliverPreset(claimedPreset, ...);
    log.info("processOneScheduledDelivery: deliverPreset returned cleanly for presetId={}",
        claimedPreset.getId());
    return true;
} catch (Exception ex) {
    log.warn("processOneScheduledDelivery: deliverPreset threw for presetId={}: {}: {}",
        claimedPreset.getId(), ex.getClass().getName(), ex.getMessage(), ex);
    throw ex;
} finally {
    popAuthentication(previous);
}
```

Plus a loop-entry log:
```java
log.info("processDueScheduledDeliveries: now={} duePresetCount={}", now, duePresets.size());
```

### Why INFO not DEBUG

- This is load-bearing diagnostic for an unblocked CI gate
- INFO appears in the live ecm-core log artifact; DEBUG might be
  filtered out depending on the runtime profile
- After the lane closes, these can be demoted to DEBUG in a
  follow-up commit

### Why the new catch+rethrow

`processOneScheduledDelivery` previously had only `try { ... }
finally { popAuthentication }`. If `deliverPreset` threw, the
exception propagated up unlogged from this layer. Now we log the
exception class+message at WARN before rethrowing. The rethrow
preserves the existing behavior: the exception still propagates to
`processDueScheduledDeliveries`'s loop catch, which logs and
continues.

## What the next CI run will tell us

Reading the inner-loop INFO logs:

| Pattern observed | Conclusion |
|------------------|------------|
| `duePresetCount=0` | `findByScheduleEnabled...` not seeing the preset → tx visibility / timing issue |
| `enter presetId=...` then `claim CAS lost` | The CAS isn't matching → likely timestamp precision; fix is to broaden the CAS query or store the read-value reliably |
| `enter` then `calling deliverPreset` then `deliverPreset threw` with class+message | Failure inside `deliverPreset`; the exception class+message names the next root cause |
| `enter` then `calling deliverPreset` then `returned cleanly` (and processedCount still 0) | Logic bug in `processOneScheduledDelivery`'s return path or the loop's count increment — extremely unlikely given the code |

The third case is by far the most useful — most likely path is
`persistFailedExecution` throwing, which we now catch+rename in the
500 body via the controller catch already.

## Verification

### Local
- `git diff --check` clean on the touched file
- Backend tests delegated to CI (Docker not running locally)

### Expected CI signal on `8ad99f7`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ — no test logic change, just log statements |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate / notification step** | **Still ❌** (this is diagnostic, not a fix) |
| Phase 5 Mocked | Awaiting verdict from PR-148/150/151/152 rollout |

The artifact's `ecm-core.log` is the place to read the new logs.
Even with rotation to 200 lines, the most recent loop's logs
should fit.

## Files Changed

| File | Lines |
|------|-------|
| `ecm-core/.../service/RmReportPresetDeliveryService.java` | +16 |

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145..146 | Diagnostic catch + handler | ✅ shipped — surfaces 500 body |
| PR-148/150/151/152 | Phase 5 Mocked rollout | ✅ shipped — verifying |
| PR-149 + fixup | REQUIRES_NEW isolation | ✅ shipped + Backend ✅ |
| **PR-153** | **processOneScheduledDelivery diagnostic logs** | **✅ shipped this turn** |
| PR-154 | Targeted fix (after `8ad99f7` CI names the cause) | Pending |

## Sequencing decision

Each diagnostic round has named one new layer of the failure mode:

1. PR-145 — body-level catch wired up (exposed it didn't catch)
2. PR-146 — controller-boundary catch + 500 body has message
   (exposed `UnexpectedRollbackException`)
3. PR-149 — REQUIRES_NEW isolation (eliminated rollback; exposed
   `processedCount: 0`)
4. **PR-153** — inner-loop logs (exposes which branch gives 0)
5. PR-154 — targeted fix once cause is named

The cadence has been "name one layer per CI round" — slow but
deterministic. After PR-154 the gate should be green; if not,
the same loop continues with the next named cause.

## Non-goals

- Did not speculatively fix the CAS query or change
  `claimScheduledRun` — without naming the cause, a guess fix
  could mask a different bug or introduce a regression
- Did not adjust the test — the test correctly expresses operator
  intent (one due preset → expect one processed)
- Did not change the controller catch or handler — those are
  working correctly (5xx body now carries `message`)
- Did not touch Phase 5 Mocked — separate rollout in flight
- Did not move logs behind a feature flag — they're INFO and
  intended to be temporary; demote to DEBUG after the lane closes
