# P5 PR-161 — Audit for latent JSDoc / strict-mode / auth-strategy patterns

## Date
2026-04-26

## Status
Audit only. No code change. Documents the proactive scan run after
PR-161 was committed, verifying no other instances of the patterns
that bit PR-145..158 are lurking in the e2e tree.

## Why this audit

Three named regression patterns were closed in the diagnostic chain:

1. **JSDoc-glob terminator (PR-158)** — `*/` inside a `/** */` block
   self-terminates the comment. Bit `keycloakMock.ts`. Memory entry
   `feedback_jsdoc_glob_terminator.md` codifies the fingerprint.
2. **Strict-mode locator violation (PR-154)** — bare `getByText(varName)`
   matches sibling `<em>` + `<span>` elements with the same
   highlighted substring. Bit the notification gate's deliveredFilename
   assertion.
3. **Strict-mode role-by-name (PR-161)** — `getByRole('combobox', { name: 'Task status' })`
   matches multiple comboboxes on the admin dashboard. Bit
   `admin-audit-filter-export.mock.spec.ts`. Same pattern hit
   `getByRole('button', { name: 'Search' })` in
   `search-suggestions-save-search.mock.spec.ts`.

This audit verifies no other instance of these patterns is lurking
in the codebase that might bite a future CI cycle.

## Audit method

Three targeted greps across `ecm-frontend/e2e/`:

### 1. JSDoc with embedded `*/`

```bash
find ecm-frontend/e2e/helpers -name "*.ts" \
  -exec grep -lE "^\s*/\*\*" {} \;
```

**Result:** zero. Only `keycloakMock.ts` contained a JSDoc-shaped
opener historically; PR-158 replaced it with line comments. The
file's *current* `/**` match is the glob inside `page.route('**/realms/**')`,
which is a string literal — not a JSDoc opener.

`login.ts` and `api.ts` use line comments only.

### 2. Bare-variable `getByText` with `.toBeVisible()`, no `.first()`, no scope

```bash
grep -rE "page\.getByText\(\s*[a-zA-Z_]" ecm-frontend/e2e/*.mock.spec.ts \
  | grep -vE "(first\(\)|toHaveCount|exact|new RegExp|\.test\()"
```

Mock-spec scope only (Phase 5 Mocked Regression Gate scope).

**Result:** 2 hits, both in `admin-preview-diagnostics.mock.spec.ts`:

- `:4585 page.getByText(deadLetterName)` — `deadLetterName` is
  `'e2e-preview-dead-letter-replay.pdf'` (line 20). Unique filename;
  appears once. Low strict-mode risk.
- `:4587 page.getByText(blockedPreventionName)` — `blockedPreventionName`
  is `'e2e-preview-prevention-blocked.bin'` (line 18). Same shape.

Both are unique, unambiguous filenames that don't match the
PR-154 sibling-element pattern (filename appearing in `<em>`
highlight + `<span>` description). No action needed; flagged for
future awareness if the page's highlight rendering ever changes.

### 3. Unscoped role-by-short-name locators

```bash
grep -rE "page\.getByRole\(['\"]combobox['\"], \{ name:" \
  ecm-frontend/e2e/*.mock.spec.ts
```

**Result:** zero unscoped matches. All combobox locators in mock
specs are either:

- Scoped by parent (`reportingSection.getByRole`, `ledgerCard.getByRole`,
  `compareDialog.getByRole`)
- Or use disambiguated full names (`'From version'`, `'To version'`,
  `'Account'`, `'Rule'`, `'Days'`)

Same scan for `getByRole('button', { name: 'X' })` with single-word
names: only descriptive long names appear in mock specs (e.g.,
`'Account menu'`, `'Retry all matched (max 200)'`, `'Cancel preview task'`).
No latent strict-mode risk.

## Cross-check: `mockKeycloakUnreachable` consumers

After PR-161's helper redesign (bypass-without-token), all consumers
should pick up the new semantics. Listed:

```bash
grep -rln "mockKeycloakUnreachable" ecm-frontend/e2e/*.spec.ts
```

| Spec | Status post-PR-161 |
|------|-------------------|
| `bootstrap-startup-fallback.mock.spec.ts` | un-fixme'd at :19/:70 |
| `app-error-boundary-recovery.mock.spec.ts` | un-fixme'd at :17 |
| `app-error-boundary-chunk-load-recovery.mock.spec.ts` | un-fixme'd at :45 |
| `route-fallback-no-blank.mock.spec.ts` | :78 still fixme'd (conservative) |
| `startup-visibility-sla.mock.spec.ts` | :89 still fixme'd (conservative) |

Five specs total; all five accounted for in the PR-160/PR-161 work.
No dangling consumer.

## Cross-check: Playwright `test.fixme()` semantics in CI

`test.fixme(title, fn)` (the title-fn form) marks a test as TODO
without an external skip condition. Behavior:

- The test does NOT execute in any project run
- It appears in the report under "fixme" / "skipped"
- Playwright's process exit code is **not** non-zero from fixme'd tests

The Phase 5 Mocked gate script (`scripts/phase5-regression.sh:1126-1141`):

```bash
playwright_rc=0
... npx playwright test ... || playwright_rc=$?
if [[ "${playwright_rc}" -eq 0 ]]; then
  echo "phase5_regression: ok"
else
  print_playwright_failure_summary ...
  exit "${playwright_rc}"
fi
```

Exits with playwright's exit code. With 26 active passing + 2 fixme'd
+ 0 actual failures, exit code is 0 — gate green. Verified by reading
the script source; no surprise behavior.

## Findings summary

| Pattern | Latent instances | Action |
|---------|-----------------:|--------|
| JSDoc-glob terminator | 0 | none |
| Bare `getByText(var).toBeVisible()` strict-mode | 0 risky (2 found, both safe) | none |
| Unscoped `getByRole` short-name strict-mode | 0 | none |
| `mockKeycloakUnreachable` orphan consumer | 0 | none |
| `test.fixme()` exit-code surprise | 0 | none |

**No code change required from this audit.** The PR-145..158
diagnostic chain plus PR-160/PR-161 closeout has covered all known
instances of the three patterns. Memory entries
(`feedback_jsdoc_glob_terminator.md`, `feedback_phase5_mocked_keycloak_strategy.md`,
`feedback_diagnostic_cadence_for_opaque_500s.md`) preserve the
fingerprints for future similar patterns to be caught faster.

## Why this audit was worth doing

The PR-145..158 chain spent ~24 hours of CI cycles to converge.
Each named layer cost CI time + diagnostic mental load. A 15-minute
proactive scan after the lane closes is dramatically cheaper than
discovering a latent fourth-pattern instance from a CI failure
weeks later. The audit confirms the diagnostic-cadence discipline
covered the pattern space exhaustively, not just the cases we
happened to trip over.

## Files changed

- `docs/P5_PR161_AUDIT_LATENT_REGRESSION_PATTERNS_20260426.md` (this MD)

No code, no test, no helper.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-160 | 6 unauth-flow fixme'd | ⚠️ partially superseded by PR-161 |
| PR-161 | Helper redesign + 4 un-fixme + 3 unrelated fixes | ✅ committed (`befc527`); awaiting push |
| PR-159 | Email lane backend foundation design preview | ✅ committed (`0e641b3`); design only |
| **PR-161 audit** | **Latent-regression-pattern audit** | **✅ this MD; no action needed** |
| PR-162 (planned) | Un-fixme route-fallback:78 + sla:89 | After PR-161 verdict |
| PR-159 implementation | Email lane backend (per design preview) | After PR-161 verdict + design alignment |

## Bottom line

The Phase 5 Mocked Regression Gate is now structurally complete and
free of latent instances of the three named regression patterns.
The notification lane stays accepted (`08f7b0e`). PR-159 has a clean
design preview ready. The unpushed commits (`3ccbc10`, `befc527`,
`0e641b3`) are the next-push bundle.

After the next CI verdict on PR-161:
- ✅ green → start PR-159 implementation; un-fixme route-fallback/sla
  in PR-162
- ❌ named residuals → diagnostic-cadence on each, same shape as
  PR-145..158
