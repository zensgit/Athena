# P5 PR-154 — Notification Gate Locator Fix (Diagnostic Chain Closure)

## Date
2026-04-26

## Scope

Single-line targeted test-locator fix that closes the PR-145 → PR-153
diagnostic chain. The backend was structurally correct after PR-149's
REQUIRES_NEW isolation; the residual gate failure was a Playwright
strict-mode-violation in test `:914`'s post-navigation locator,
flaky-by-render-timing.

E2E test only. No backend change. No frontend production code.

## Why this is the closing slice

The latest two CI runs that contain PR-148 → PR-152 named the exact
failure mode that no prior round could see:

| Run | Notification gate | Failure shape |
|-----|-------------------|---------------|
| `8410eaf` (PR-148+149+150+151) | ✅ "1 flaky, 3 passed" → step success | `:914` failed initial run, retry #1 passed (timing race) |
| `3708ba8` (adds PR-152) | ❌ all 3 retries failed | `:914` strict-mode-violation on every retry |

Both fail the same line, same assertion:

```
strict mode violation: getByText('e2e-rm-success-notification-...csv')
resolved to 2 elements:
    1) <em>e2e-rm-success-notification-1777174394550-20260426.csv</em>
    2) <span>Delivered e2e-rm-success-notification-...</span>
```

Substring match in `getByText(...)` catches both elements. By the
time the assertion runs, sometimes one is visible and the other
isn't (reordering / collapse during render); sometimes both are.
Whichever way the timing rolls determines whether the strict-mode
check fires.

This is exactly the kind of bug PR-149's REQUIRES_NEW couldn't fix
— the backend is fine, the test locator is ambiguous.

## Design

One line:

```diff
- await expect(page.getByText(deliveredFilename)).toBeVisible({ timeout: 60_000 });
+ await expect(page.getByText(deliveredFilename, { exact: true })).toBeVisible({ timeout: 60_000 });
```

The `<em>` element contains exactly the filename text. The `<span>`
contains `"Delivered <filename>..."` — a longer string. With
`exact: true`, the locator now matches only the heading, not the
audit caption.

Plus a comment explaining the cause and the historical evidence
(8410eaf's retry-1 luck, 3708ba8's deterministic failure) so the
next person reading this assertion knows why it's `{ exact: true }`.

## Diagnostic chain summary (closed by this PR)

| Round | Slice | Named cause |
|-------|-------|-------------|
| 1 | PR-145 (service-body catch + handler) | "Catch wired but body still empty" → catch outside reach |
| 2 | PR-146 (controller-boundary catch) | `UnexpectedRollbackException` from outer-tx pollution |
| 3 | PR-146 NOT_SUPPORTED workaround | `InvalidDataAccessApiUsageException` — workaround broke `flushAutomatically` |
| 4 | PR-149 (REQUIRES_NEW isolation) | `processedCount: 0` (200 OK, but not counting deliveries) |
| 5 | PR-153 (inner-loop INFO logs) | (logs were never read from a successful CI artifact — the gate flipped to "1 flaky, 3 passed" before PR-153's run, exposing the locator issue directly) |
| **6** | **PR-154 (this slice)** | **Strict-mode-violation on filename locator — fixed with `{ exact: true }`** |

The chain produced six rounds of evidence over ~24 hours of CI
turnaround time. Cumulative effort more than a one-shot guess fix
would have been, but every round was small, reversible, and
verified.

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` — clean
- `npm run lint` — clean
- Cannot run e2e locally (requires the live Docker stack); CI is
  the authoritative runner

### Expected CI signal on `088f55e`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend) |
| Frontend Build & Test | ✅ unchanged (no Jest tests touched) |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate / notification step** | **✅ deterministic** — locator now matches only one element, no more strict-mode violation, no more retry-luck dependency |
| Phase 5 Mocked Regression Gate | Pre-existing (PR-148/150/151/152 rollout's own track — separate verdict) |

If green: the notification lane is **finally** ready to flip from
`pending` to `accepted`. PR-155 = single docs commit doing the flip.
PR-156+ = email lane.

## Files Changed

| File | Lines |
|------|-------|
| `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts` | +7 / -1 |

No production code change. No backend. No migration.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145 | Service-body catch + handler | ✅ shipped |
| PR-146 | Controller-boundary catch + tx workaround | ✅ shipped |
| PR-148/150/151/152 | Phase 5 Mocked Keycloak-hang rollout | ✅ shipped (separate track) |
| PR-149 + fixup | REQUIRES_NEW per-preset isolation | ✅ shipped |
| PR-153 | Inner-loop INFO logs | ✅ shipped (in-tree as residual diagnostic) |
| **PR-154** | **Strict-match locator on filename** | **✅ shipped this turn** |
| PR-155 | Flip notification closeout to `accepted` | After CI verdict |
| PR-156+ | Email delivery channel | After PR-155 |

## Memory entry implications

The entry `feedback_diagnostic_cadence_for_opaque_500s.md` shipped
last turn captured the pattern. PR-154 is the closing example. The
cadence hit each layer cleanly:

1. Get the response body to carry `class: message`
2. If the catch is wired but body still empty, the catch is outside
   reach — move it up
3. After the cause is named, decide between workaround and
   structural fix
4. After structural fix, add inner-loop logs if the residual symptom
   isn't directly observable
5. After logs, the actual cause is named — apply the targeted fix

Each step verified before moving to the next. Future similar lanes
should follow the same shape; the memory entry preserves that.

## Non-goals

- Did not demote PR-153's INFO logs to DEBUG yet — leave them as
  insurance until the lane has run green for several CI rounds; a
  follow-up demote can land in PR-156 area.
- Did not investigate why `:914` started rendering the filename
  twice (the audit caption was probably introduced by the
  PR-123..133 notification lane code itself; the test was written
  before the audit caption existed). Fixing the test is faster
  and lower-risk than refactoring the audit caption rendering.
- Did not investigate Phase 5 Mocked further — separate track,
  PR-151's mockKeycloakUnreachable approach has not yet had its
  verdict because every Phase 5 Mocked job has cancelled at the
  30-min cliff. That's a separate investigation slice.

## What success means after this CI

The notification lane has been the focus for ~24 hours of CI cycles.
Once `088f55e` lands green:

- 6 diagnostic rounds → 1 named cause → 1 single-line fix
- The lane's structural correctness (PR-149) was already proven
- The closing fix is a 7-line test change

That's the diagnostic discipline the memory entry codified working
end-to-end.
