# Design: Preview/Search Stability Hardening (2026-02-14)

## 1. Background
Recent full-stack verification exposed three high-impact stability gaps:
1. `ecm-core` container restart loop in Docker runtime.
2. Runtime coupling between LibreOffice and startup path (JODConverter hard-enabled).
3. Search fallback E2E expectation drift from current product behavior.

The target is to stabilize startup + search fallback governance verification without broad API changes.

## 2. Scope
In scope:
- Runtime startup hardening for `ecm-core` container.
- Docker compose configurability for JODConverter enablement.
- E2E assertion alignment with current fallback UX behavior.
- Executable 7-day delivery plan for this stabilization stream.

Out of scope:
- Re-architecture of conversion pipeline.
- Full redesign of search fallback policies.
- Production deployment scripts.

## 3. Root Causes
### 3.1 Core restart loop
- Symptom: `athena-ecm-core-1` repeated restart, health endpoint unavailable.
- Cause A: `entrypoint.sh` used `su ... -c "java ..."`; after user switch, PATH no longer resolved `java` reliably.
- Cause B: For fast local image build (`SKIP_LIBREOFFICE=true`), runtime still enforced `JODCONVERTER_LOCAL_ENABLED=true` and failed startup when office home missing.

### 3.2 Fallback test drift
- `search-fallback-criteria.spec.ts` assumed direct zero-result rendering.
- Current product behavior may briefly surface stale fallback panel with action `Hide previous results`.

## 4. Design Decisions
### Decision 1: Absolute Java binary in runtime entrypoint
- File: `ecm-core/entrypoint.sh`
- Use absolute default `/opt/java/openjdk/bin/java` with fallback to `command -v java`.
- Reason: Decouple startup from shell PATH differences after `su`.

### Decision 2: Make JODConverter enablement overridable in compose
- File: `docker-compose.yml`
- Change `JODCONVERTER_LOCAL_ENABLED=true` to `${JODCONVERTER_LOCAL_ENABLED:-true}`.
- Reason: Keep current default behavior but allow local override (`false`) when running lightweight image without LibreOffice.

### Decision 3: Adapt E2E fallback assertion to governed behavior
- File: `ecm-frontend/e2e/search-fallback-criteria.spec.ts`
- If fallback panel appears, click `Hide previous results` before asserting no stale cards.
- Keep assertion for no indexing-warning panel and empty result state.
- Reason: Preserve test intent (no stale carry-over in final visible state) while matching actual UX controls.

### Decision 4: Add dual restart modes for local loops
- File: `scripts/restart-ecm.sh`
- Introduce `--mode fast|full` (`--fast`, `--full`) with default `full`.
- `fast`: skip LibreOffice build payload and disable local JODConverter.
- `full`: keep conversion chain enabled.
- Reason: separate productivity loop and full-conversion validation path.

### Decision 5: Add dedicated preview/search CI gate entrypoint
- Files: `ecm-frontend/package.json`, `.github/workflows/ci.yml`
- Expose one npm command for preview/search critical E2E pack and invoke it in CI core gate job.
- Reason: stable, reusable, and explicit coverage for the regression-prone surface.

## 5. 7-Day Detailed Plan
### Day 1: Runtime startup hardening
- Deliverables:
  - Fix Java binary resolution in entrypoint.
  - Rebuild and verify container boot logs.
- Acceptance:
  - `ecm-core` no longer fails with `java: not found`.

### Day 2: Conversion toggle decoupling
- Deliverables:
  - Compose variableized JODConverter enable flag.
  - Local fast-run profile using `JODCONVERTER_LOCAL_ENABLED=false`.
- Acceptance:
  - Core starts healthy without LibreOffice image payload.

### Day 3: Search fallback E2E stabilization
- Deliverables:
  - Update stale fallback criteria test logic.
  - Re-run target fallback governance specs.
- Acceptance:
  - `search-fallback-criteria.spec.ts` passes consistently.

### Day 4: Preview/search regression pack
- Deliverables:
  - Run combined regression set:
    - `phase5-fullstack-admin-smoke`
    - `search-preview-status`
    - `advanced-search-fallback-governance`
    - `search-fallback-criteria`
- Acceptance:
  - All selected tests pass in one run.

### Day 5: Failure triage automation
- Deliverables:
  - Add script target for grouped preview/search E2E execution and artifact capture.
- Acceptance:
  - One command produces deterministic test bundle + artifacts.

### Day 6: Documentation consolidation
- Deliverables:
  - Design + development/verification markdown updates.
- Acceptance:
  - Docs include root cause, decision records, commands, and outcomes.

### Day 7: Release readiness check
- Deliverables:
  - Final smoke rerun.
  - Risk list and rollback notes.
- Acceptance:
  - Clean pass or explicit residual-risk signoff.

## 6. Risks and Mitigations
- Risk: Disabling JODConverter hides office conversion issues.
  - Mitigation: Keep default `true`; only disable via explicit env override for lightweight local loops.
- Risk: Fallback behavior keeps evolving and breaks text assertions again.
  - Mitigation: Prefer behavior/assertion on controls and visibility state, not brittle static copy.

## 7. Rollback
- Revert the three touched files if needed:
  - `ecm-core/entrypoint.sh`
  - `docker-compose.yml`
  - `ecm-frontend/e2e/search-fallback-criteria.spec.ts`
