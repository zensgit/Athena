# CI Post-Push Fixes — 2026-04-20

## Context

After pushing the full P0B → P5 backlog to `origin/main` on 2026-04-18, two consecutive CI runs both failed:

- Run `24595384668` (commit `e479340`): Frontend + Phase C Security failed
- Run `24595449247` (commit `8a4a4d7`, after initial lint fix): Same two jobs still failed

Backend Verify passed both times — backend code + 16 new migrations boot cleanly on CI PostgreSQL. All failures were in the CI pipeline itself or in pre-existing frontend warnings that CI treats as errors.

This session diagnosed and fixed three distinct issues, integrated Codex's parallel healthcheck review, and pushed PR-80 (saved-search RM projection fix).

## Issues Found

### Issue 1 — Frontend build fails on unused MUI imports (CI only)

**Symptom**:
```
[eslint]
src/components/share/ShareLinkManager.tsx
  Line 37:3:  'BarChart' is defined but never used  @typescript-eslint/no-unused-vars

src/pages/AdminDashboard.tsx
  Line 70:3:  'FilterList' is defined but never used  @typescript-eslint/no-unused-vars

Failed to compile.
Treating warnings as errors because process.env.CI = true.
```

**Root Cause**: Local `npm run lint` reports these as warnings (non-blocking). But CI runs `npm run build` which invokes `react-scripts build`. When `CI=true` is set (GitHub Actions default), react-scripts promotes all ESLint warnings to compile errors. The two pre-existing unused imports were therefore harmless locally but fatal in CI.

**Why it wasn't caught earlier**: Session 2026-04-13 verification ran only `npm run lint`, not `CI=true npm run build`. The two warnings were explicitly noted in `docs/CI_PIPELINE_DESIGN_20260413.md` as "2 unused imports (non-blocking)" — but "non-blocking" was wrong under CI=true.

**Fix**: Removed both unused imports.

**Files Changed**:
- `ecm-frontend/src/components/share/ShareLinkManager.tsx` (-1 line: `BarChart`)
- `ecm-frontend/src/pages/AdminDashboard.tsx` (-1 line: `FilterList`)

**Local Verification**:
```
CI=true npm run build → "The build folder is ready to be deployed."
npm run lint → 0 errors, 0 warnings
```

---

### Issue 2 — Phase C Security timeout (exit code 124)

**Symptom**:
```
#14 67.33 [INFO] Resolved dependency: error_prone_annotations-2.23.0.jar
##[error]Process completed with exit code 124.
```

Exit 124 on GitHub Actions = job-level `timeout-minutes` hit.

**Root Cause**: The Phase C "Start verification stack" step runs `docker compose build --no-cache ecm-core` before starting the container. `--no-cache` defeats all BuildKit layer caching, so every CI run fully re-downloads the ~500MB Maven dependency tree. With a 30-minute job timeout, this fails even when the build would eventually succeed. Backend Verify had already compiled the project in a prior job — Phase C did not need a clean rebuild.

**Fix** in `.github/workflows/ci.yml`:

| Change | Before | After |
|--------|--------|-------|
| Build flags | `docker compose build --no-cache ecm-core` | `docker compose build --build-arg SKIP_LIBREOFFICE=true ecm-core` |
| Health-wait timeout | `timeout 180` | `timeout 240` |
| Job timeout-minutes | `30` | `45` |

`SKIP_LIBREOFFICE=true` was already the pattern used in `acceptance_smoke` and `frontend_e2e_core` jobs — Phase C was inconsistent.

---

### Issue 3 — ecm-frontend / nginx healthcheck used `localhost` inside container (Codex review)

**Symptom** (observed locally, not in CI logs): ecm-frontend container status `unhealthy` even though HTTP 200 served correctly from host. Container-local `wget http://localhost:80/` returned `Connection refused`; `wget http://127.0.0.1:80/` succeeded.

**Root Cause**: On some container images, `localhost` resolves to IPv6 `::1` while nginx only binds to IPv4 `0.0.0.0`. This produced false-negative healthchecks and cascade-blocked `nginx` service from starting under `depends_on: condition: service_healthy`.

**Fix** in `docker-compose.yml` (Codex's change, merged into this commit):
- `ecm-frontend`: `wget -qO- http://localhost:80/` → `http://127.0.0.1:80/`
- `nginx`: `wget -qO- http://localhost:80/health` → `http://127.0.0.1:80/health`

---

## Codex's Parallel Delivery: PR-80

While this session diagnosed CI, Codex delivered **P5 PR-80: Saved-Search RM Record Projection Fix**.

**Problem**: Saved-search execution (`SavedSearchesPage → Run saved search → SearchResults?savedSearchId=...`) dropped RM projection fields (`record`, `declared*`, `recordCategory*`) before results reached `SearchResults`, breaking `RecordStatusChip` rendering on declared records opened via this path.

**Scope**:
- Extended `savedSearchService.SearchResultItem` to reflect RM fields already in backend payload
- Added dedicated mapper in `nodeSlice` for saved-search execution results
- New e2e spec `saved-search-record-projection.spec.ts`
- Zero backend changes

**Closes the gap** left after PR-76/77/78/79 where regular + advanced search carried RM projection but saved-search execution did not.

---

## Commits

| Commit | Scope | Files | Delta |
|--------|-------|-------|-------|
| `8236a8e` | CI fixes: unused imports + Phase C timeout + healthcheck | 7 | +153 / -8 |
| `b5aafe5` | PR-80 saved-search RM projection + related docs | 12 | +452 / -45 |

Both commits pushed to `origin/main`, triggering CI run `24650183138`.

---

## Verification Status

### Local

- `CI=true npm run build` — passes
- `npm run lint` — 0 errors, 0 warnings
- Existing ecm-core container — still healthy after 6 days uptime
- Playwright acceptance smoke (3 tests) — passed in previous session
- Targeted Mockito suites (RM + Search + ContentReference) — BUILD SUCCESS

### CI Run `24650183138` (in-flight at time of writing)

| Job | Status | Notes |
|-----|--------|-------|
| Backend Verify | **success** | Second consecutive pass on this backlog |
| Frontend Build & Test | in_progress | Watching for build step |
| Phase C Security Verification | in_progress | Watching for timeout |
| Acceptance Smoke (3 admin pages) | queued | Requires backend + frontend green |
| Frontend E2E Core Gate | queued | Requires backend + frontend green |
| Phase 5 Mocked Regression | queued | Requires frontend green |

---

## Lessons Learned

1. **`npm run lint` ≠ `CI=true npm run build`**. react-scripts has its own warning-as-error policy keyed on `CI=true`. Any CI verification must reproduce this environment. Local `npm run lint` alone is insufficient as a pre-push gate.

2. **`--no-cache` in CI build steps is almost always wrong**. BuildKit layer caching is the only reason Maven-backed Docker builds stay under 30 minutes. Use `--no-cache` only when pinpointing a caching bug, never as a default.

3. **Healthcheck hosts: prefer `127.0.0.1` over `localhost` in container contexts**. Dual-stack IPv4/IPv6 resolution inside containers is an easy source of false negatives that only surface in cascade (dependent services stuck on `condition: service_healthy`).

4. **Run CI locally once before promoting to shared infrastructure**. Two failed CI runs would have been avoided by `CI=true npm run build` before the first push.

---

## Open Items

- This session did not run the full `./mvnw test` suite (still deferred as in the 2026-04-18 report)
- Once CI `24650183138` completes, the full acceptance_smoke + frontend_e2e_core + phase_5_mocked jobs will run for the first time against this backlog
- P5_PR79 record-category-path facet filter and P5_PR80 saved-search fix are shipped as additive enhancements; no regression surface added

---

## Sign-Off Conditions

CI run `24650183138` should reach **all green** if:

1. Issue 1 fix valid → Frontend Build & Test passes
2. Issue 2 fix valid → Phase C Security Verification passes within 45 min
3. Acceptance Smoke + E2E Core Gate pass (first time running against full backend)

If any of these still fail, follow-up fixes go in a separate commit with a linked report under `docs/`.
