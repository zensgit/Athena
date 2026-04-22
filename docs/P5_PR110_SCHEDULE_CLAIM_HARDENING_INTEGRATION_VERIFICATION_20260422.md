# P5 PR-110 Schedule Claim-Before-Upload Hardening — Integration & Verification

## Date
2026-04-22

## Scope

Documentation-only integration note. The runtime change for
claim-before-upload already shipped as part of commit `bbf588f`
(PR-109). Codex has now produced standalone design + verification
docs for this specific hardening aspect, and the intake matrix has
been updated to list PR-110 as a distinct slice.

## Why docs-only this turn

PR-109 bundled two concerns: (a) schedule metadata on the preset
list + page-level drilldown, and (b) atomic CAS claim of due presets
in the scheduler loop. The CAS claim is important enough on its own
that it warrants a dedicated slice writeup for anyone reviewing the
concurrency story later.

Splitting the docs (not the code) keeps:
- git history intact — no re-shaping of the committed change
- rollup accurate — the runtime capability landed when it landed
- searchability good — "PR-110 claim hardening" finds the concurrency
  design without spelunking through PR-109's doc

## Runtime behavior (from `bbf588f`)

**Before** (pre-PR-109 runner):
```
scan due presets → for each: upload CSV → save preset with new nextRunAt
```
Two overlapping scheduler instances could both scan the same preset,
both upload, and only one save would trip optimistic locking.

**After** (PR-109 + this PR-110 doc):
```
scan due presets → for each: CAS claimScheduledRun → reload → upload CSV
```
`claimScheduledRun` atomically advances `nextRunAt` while checking
the caller saw the expected previous value. Only one instance wins;
others skip. The preset is reloaded after the claim so the
subsequent upload sees the post-claim state, and entity_version has
been incremented so any later save will go through the normal
optimistic-lock path.

Relevant code (from PR-109, unchanged by this commit):

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update RmReportPreset p
       set p.nextRunAt = :nextRunAt,
           p.entityVersion = p.entityVersion + 1
     where p.id = :presetId
       and p.deleted = false
       and p.scheduleEnabled = true
       and p.nextRunAt = :expectedNextRunAt
""")
int claimScheduledRun(
    @Param("presetId") UUID presetId,
    @Param("expectedNextRunAt") LocalDateTime expectedNextRunAt,
    @Param("nextRunAt") LocalDateTime nextRunAt
);
```

## Verification

No new verification this turn — the runtime behavior is covered by
the PR-109 tests (32/32 backend + 131/131 frontend green on `bbf588f`
and `797350e`).

```
# Re-check still-green on HEAD tip post-docs commit
cd ecm-core && ./mvnw -B test \
  -Dtest='RmReportPresetDeliveryServiceTest,RmReportPresetControllerTest,RmReportPresetServiceTest,RecordsManagementControllerSecurityTest'
→ (unchanged) 32/32 green
```

## Files Changed in This Commit (`46fe64d`)

| File | Kind |
|------|------|
| `docs/P5_PR110_RM_REPORT_PRESET_SCHEDULE_CLAIM_HARDENING_DESIGN_20260422.md` | Codex design writeup |
| `docs/P5_PR110_RM_REPORT_PRESET_SCHEDULE_CLAIM_HARDENING_VERIFICATION_20260422.md` | Codex verification writeup |
| `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_DEVELOPMENT_20260417.md` | +4 lines (PR-110 entry) |
| `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_VERIFICATION_20260417.md` | +1 line (PR-110 entry) |

No source code change. No migration.

## Expected CI Outcome

Docs-only commit — every gate should pass identically to the prior
`bbf588f` / `797350e` runs.

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..98 | Scheduled delivery core | ✅ |
| PR-99..100 | UI hardening + mocked e2e | ✅ |
| PR-101..104 | Full-stack e2e + ledger API + ledger UI | ✅ |
| PR-105 | Ledger operator polish | ✅ |
| PR-106 | Delivery folder tree picker | ✅ |
| PR-107 | Telemetry endpoint | ✅ |
| PR-108 | Telemetry card | ✅ |
| PR-109 | Schedule metadata + drilldown + claim CAS query | ✅ |
| **PR-110** | **Claim-before-upload hardening writeup** | **✅ docs shipped (runtime already landed in PR-109)** |

## Non-goals

- No source code change in this commit
- No new migration
- No adjustment to PR-109 test coverage — the CAS path is already
  covered by the scheduler tests that went green with `bbf588f`
