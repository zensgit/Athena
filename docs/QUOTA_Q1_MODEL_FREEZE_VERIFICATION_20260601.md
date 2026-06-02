# Quota Q1 — Model Freeze Verification (2026-06-01)

ADR-002 quota accounting model frozen and implemented (Q1 of the ADR-002 follow-up). Gate-reviewed and CI-green.

## Model (frozen, per gate review)

> usedBytes = sum(live `Document.fileSize` under tenant root) + sum(non-current retained `Version.fileSize` under tenant root)

- Current-version bytes are counted once via the documents sum. The initial version references the current `contentId` (`InitialVersionProcessor`), so `live + all versions` would double-count — the version sum **excludes each document's `currentVersion`**.
- **No physical blob dedup** — logical per-tenant accounting (ADR-001 global dedup makes per-tenant physical accounting ill-defined; reopening requires reopening ADR-001).
- `ContentService` dedup fast path enforces quota with the incoming size.

## Implementation (commit `fbbe256`)

- `VersionRepository.sumNonCurrentVersionFileSizeByPathPrefix` (excludes `currentVersion`)
- `TenantQuotaService.calculateUsedBytes` = live docs + non-current versions
- `ContentService.storeContent` dedup branch → `assertQuotaAvailable(incoming size)`
- `TenantQuotaServiceTest`: non-current versions count toward quota / initial version not double-counted; 4-arg fixture updated
- ADR-002 addendum records the frozen model

## CI verification

GitHub Actions run `26802079561` (sha `fbbe256`): **completed/success**, all 7 jobs green:
- **Backend Verify: success** — the JPQL (`v.document.currentVersion.id`, tenant path scoping) resolves at `@SpringBootTest` context start (a bad path would fail context boot), and the quota tests pass.
- Frontend Build & Test, Phase C Security, Acceptance Smoke, Property Encryption Closeout, Phase 5 Mocked Regression, Frontend E2E Core: success.
- No docker-registry flake this run.

## Still open

**Q2** — `TenantContext` propagation into async/background content-writing paths (e.g. `BulkImportService` thread-pool, transfer receiver writes). Separate slice; read-only inventory/brief first (ADR-002 Next Step #2).
