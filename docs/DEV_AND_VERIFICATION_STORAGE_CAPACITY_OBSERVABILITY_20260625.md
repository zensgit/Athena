# Storage Capacity Observability — Development & Verification

- Date: 2026-06-25
- Line: Athena storage capacity observability (read-only ops observability)
- State: **COMPLETE on `main`.** taskbook → implementation → CI → review fully closed.
- Repos: Athena only. No enforcement, no quota-model change, no ADR-001/002 reopen.

## 1. State at a glance

Operators can now answer the #828-class question — **"is the actual content store nearly full?"** — from a
read-only admin endpoint + a dashboard card, with **physical disk capacity** and **tenant logical quota** kept
semantically distinct. Purely additive; the frozen quota model, the dedup design, and the upload write-path are
untouched.

| PR | What | Merge SHA |
|---|---|---|
| #34 | Day-1 taskbook — boundaries (B1-B4) + the narrow Day-2 slice | `dff6949` |
| #35 | Implementation — service + DTO + admin endpoint + dashboard card + tests | `1d1f0295` |

Local `main` == `origin/main` == `1d1f0295` at closeout.

## 2. What was built (code-grounded)

- **`StorageCapacityService`** — reads `ecm.storage.root-path`, probes the backing filestore via
  `Files.getFileStore(rootPath).getTotalSpace()` / `getUsableSpace()`; `usedBytes = totalBytes - usableBytes`
  (floored at 0); status one of `OK` / `WARN` / `CRITICAL` / `BLOCKED` / `UNKNOWN`. Thresholds are `@Value`-injected:
  `ecm.storage.capacity.warn-percent` (80), `critical-percent` (95), `blocked-min-free-bytes` (104857600 = 100 MiB);
  `BLOCKED` when `usableBytes <= blockedMinFreeBytes`. **No write side effects** — an uninspectable / missing path
  returns `UNKNOWN`, not a created directory.
- **`StorageCapacityStatusDto`** — `(backendType, status, totalBytes, usableBytes, usedBytes, usedPercent,
  warnPercent, criticalPercent, blockedMinFreeBytes, rootPath, error)`. `backendType = filesystem`.
- **`StorageAdminController`** — `GET /api/admin/storage/capacity` + `/api/v1/admin/storage/capacity`,
  `@PreAuthorize("hasRole('ADMIN')")`, returns the DTO. Global, read-only — not mixed into per-tenant metrics.
- **`TenantMetricsDashboardPage`** — renamed the existing "Total Storage Used" card to **"Tenant Logical Usage"**;
  added a **"Content Store Filesystem (Disk)"** card (status chip + used/free/total + used %); the capacity fetch is
  failure-isolated (`capacityFailed` + a toast) so it cannot break the existing tenant-metrics rendering.
- **`tenantService.getStorageCapacity()`** — the frontend client + response-shape type.

## 3. Boundaries B1-B4 — held

- **B1 — physical vs logical are separate numbers.** Physical = filestore df (`getFileStore`); logical = the
  frozen ADR-002 tenant quota. Neither is derived from the other; the dashboard labels them separately. The summed
  per-tenant logical is **not** presented as physical disk usage.
- **B2 — filesystem, not MinIO/S3.** `Files.getFileStore` on `ecm.storage.root-path`; no S3/MinIO client on the
  path. (Yuantus #828 was a host-disk-full MinIO *symptom*; Athena's content write path is filesystem, so the probe
  reads that same host-disk signal directly. A future S3 adapter would be a new taskbook.)
- **B3 — extend, not rebuild.** One card on the existing `TenantMetricsDashboardPage`; one global admin endpoint;
  the existing per-tenant metrics are reused. No new dashboard.
- **B4 — quota model frozen, read-only.** `TenantQuotaService`, the ADR-002 logical model, the `ContentService`
  write-path, and ADR-001 global dedup are all untouched. The #35 diff is additive (**+706 / -6**, the 6 deletions
  being only the dashboard card rename).

## 4. §7 ratification points — all honored

1. Physical capacity read from the filesystem behind `ecm.storage.root-path` — ✅
2. Admin read-only endpoint at `/api/admin/storage/capacity` (+ `/api/v1/...`) — ✅
3. Thresholds 80 / 95 / `blocked-min-free-bytes` = 100 MiB, overridable via `@Value` — ✅
4. `BLOCKED` is observability-only, **not** upload enforcement — ✅
5. Frontend change on `TenantMetricsDashboardPage`, not a new dashboard — ✅
6. Existing tenant quota metrics remain logical and unchanged — ✅

## 5. Verification

**CI — both PRs 7/7 green.** #35 (`run 28148733196`): Acceptance Smoke, Backend Verify, Frontend Build & Test,
Frontend E2E Core Gate, Phase 5 Mocked Regression Gate, Phase C Security Verification, Property Encryption Closeout
Gate — all `pass`. #34 was 7/7 green before merge.

**Tests (additive, included in #35):**
- `StorageCapacityServiceTest` — `OK` / `WARN` / `CRITICAL` / `UNKNOWN`, `usedBytes` never negative, no MinIO/S3.
- `StorageAdminControllerTest` — endpoint returns the DTO shape.
- `StorageAdminControllerSecurityTest` — admin-gate (the CLAUDE.md "new controller ships a `*SecurityTest`" convention).
- `TenantMetricsDashboardPage.test.tsx` — both labels render; a capacity fetch failure does not break tenant metrics.
- `tenantService.test.ts` — `getStorageCapacity()` response-shape guard.

**Local targeted run:**
```bash
cd ecm-core
./mvnw -q -Dtest=StorageCapacityServiceTest,StorageAdminControllerTest,StorageAdminControllerSecurityTest test
cd ../ecm-frontend
npm test -- --watchAll=false TenantMetricsDashboardPage tenantService
```

**Manual smoke (if a stack is up):** `GET /api/admin/storage/capacity` → `backendType=filesystem`, status
`OK|WARN|CRITICAL|BLOCKED`, numeric `totalBytes/usableBytes/usedBytes/usedPercent`; no tenant logical sum presented
as physical usage.

## 6. Final semantics (the point of the line)

- **Physical content-store capacity** = the **filesystem/disk** backing `ecm.storage.root-path` — whole-partition
  total/used/free via `getFileStore`. This is the real **"will uploads block"** signal (the disk fills, writes fail).
  It is **not** "Athena's dedup'd content bytes" — the partition is shared with the OS and anything else on it.
  Surfaced as **"Content Store Filesystem (Disk)"**.
- **Tenant logical usage** = the frozen ADR-002 per-tenant quota (live `Document.fileSize` + non-current retained
  `Version.fileSize` under tenant root). Answers **"who is consuming logical quota."** Surfaced as **"Tenant Logical Usage"**.
- The two are deliberately **distinct**: ADR-001 global dedup makes per-tenant *physical* accounting ill-defined, so
  the sum of per-tenant logical ≠ physical disk usage, and neither is derived from the other.

## 7. Out of scope (deliberate)

- **No upload-block enforcement** — `BLOCKED` is observability-only.
- **No quota-model change** — ADR-002 stays frozen; **no ADR-001 dedup change**.
- No S3/MinIO SDK, no per-tenant buckets/paths, no per-tenant physical accounting, no capacity prediction beyond
  threshold status, no host/Docker ops automation.
- **Re-entry** would each be a *new* taskbook: a real storage adapter / S3 client, or turning `BLOCKED` into upload
  enforcement.

## 8. Conclusion

Storage capacity observability is complete and verified on `main` (taskbook `dff6949` → implementation `1d1f0295`).
The #828-class operator question is answered read-only, with physical disk capacity and tenant logical quota kept
semantically distinct, without touching the frozen quota model, the dedup design, or upload enforcement.
