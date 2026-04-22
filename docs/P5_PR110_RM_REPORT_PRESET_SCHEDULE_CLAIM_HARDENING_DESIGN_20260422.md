# P5 PR-110 RM Report Preset Schedule Claim Hardening Design

## Scope

This slice hardens the shipped scheduled-delivery runner so it claims due work
before uploading CSV output.

Runtime changes in scope:

- additive repository-level scheduled-run claim method
- scheduler flow changed from `scan -> upload -> advance nextRunAt`
  to `scan -> claim -> reload -> upload`
- unit tests for the claimed happy path and already-claimed skip path

Out of scope:

- new endpoint or migration
- email delivery channel
- changing manual `deliver now` semantics

## Problem

The shipped runner previously scanned due presets and uploaded output before the
next scheduled run was advanced.

That left a duplicate-delivery window:

- two overlapping runners could both see the same due preset
- both could upload the CSV
- only the later entity save would hit optimistic-lock protection

At that point the duplicate document already existed.

## Delivered Hardening

### Repository claim step

[RmReportPresetRepository.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetRepository.java:1)

Added:

- `claimScheduledRun(presetId, expectedNextRunAt, nextRunAt)`

The claim update:

- only succeeds for the exact due `nextRunAt`
- requires `scheduleEnabled = true`
- skips deleted rows
- advances `nextRunAt` before upload
- increments `entityVersion`
- clears the persistence context automatically

### Runner flow

[RmReportPresetDeliveryService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java:1)

`runScheduledDeliveries()` now:

1. finds due presets
2. attempts to claim each one
3. reloads the claimed preset from the repository
4. only then executes upload/render

If claim returns `0`, the runner logs and skips that preset.

### Delivery semantics after claim

For scheduled runs that were already claimed:

- success path no longer recomputes `nextRunAt`
- failure path no longer recomputes `nextRunAt`

The claim is now the authoritative step that advances the schedule pointer.

Manual `deliverNow()` remains unchanged.

## Review Notes

This is intentionally a small correctness slice:

- no schema change
- no new API
- no UI change

The main value is eliminating the duplicate-upload window in overlapping or
multi-node scheduled execution.

## Recommendation

The next highest-value slice after this one is not email delivery.

More sensible next candidates are:

- extend CSV/scheduled delivery support to currently summary-only preset kinds
- or add higher-level operational E2E around the now-hardened scheduled runner
