# Storage Capacity Observability Taskbook

- Date: 2026-06-25
- Status: Day-1 taskbook; ratification required before code
- Scope: Athena storage capacity / quota observability
- Repos: Athena only
- Owner decision requested: approve the physical-vs-logical boundary and the narrow Day-2 build slice below

## 0. Executive Decision

Recommended next development line: **Storage Capacity Observability**.

The smallest useful slice is not a new quota model and not a new storage backend. It is:

1. Add a read-only physical content-store capacity probe for the filesystem path that `ContentService` actually writes to.
2. Keep existing tenant quota metrics as logical usage.
3. Extend the existing tenant metrics dashboard with a capacity card that labels these two concepts separately.

This taskbook deliberately does **not** reopen ADR-001 storage routing, ADR-002 quota accounting, per-tenant physical ownership, MinIO/S3 client work, or upload enforcement semantics.

## 1. Current Code Facts

### 1.1 Content store is filesystem/NIO from Athena's point of view

`ContentService` injects `ecm.storage.root-path` and `ecm.storage.temp-path`, then writes through Java NIO:

- `ContentService.java:52-56` injects the configured filesystem paths.
- `ContentService.java:70-79` creates and writes the upload temp file.
- `ContentService.java:98-109` derives the final storage path, creates directories, then `Files.move` / `Files.copy`.
- `ContentService.java:304-307` performs global hash lookup through `DocumentRepository.findByContentHash`.

There is no S3/MinIO content client on this write path. `docker-compose.yml` and `application-docker.yml` contain MinIO operational configuration, but the current Java content path sees a filesystem path.

Implication: Day-2 physical-capacity code should inspect the filesystem backing `ecm.storage.root-path`, e.g. via `Files.getFileStore(rootPath).getTotalSpace()` / `getUsableSpace()` after resolving or creating the content root. It must not assume a MinIO API, bucket quota, or S3 SDK.

### 1.2 Per-tenant storage usage is logical quota usage

`TenantQuotaService.calculateUsedBytes` is already the authoritative tenant quota usage calculation:

- `TenantQuotaService.java:77-80` documents the model as logical current documents plus non-current retained versions.
- The same comment explicitly states that ADR-001 global dedup makes per-tenant physical accounting ill-defined.
- `TenantMetricsService.java:27-48` exposes this value as `storageUsedBytes`, plus `quotaBytes` and `storageAvailableBytes`.

Implication: tenant metrics answer "who is consuming logical quota?" They do not answer "how full is the disk?"

### 1.3 Existing UI should be extended, not replaced

`TenantMetricsDashboardPage` already loads all tenants and their metrics, then computes `totalStorageUsed` from logical `storageUsedBytes`:

- `TenantMetricsDashboardPage.tsx:71-88` loads tenant metrics.
- `TenantMetricsDashboardPage.tsx:104-115` computes logical storage summary and chart data.
- `TenantMetricsDashboardPage.tsx:165-178` renders the current "Total Storage Used" summary card.

Implication: Day-2 should add one physical capacity card to this dashboard and relabel the existing total as logical usage. A separate "capacity dashboard" would duplicate already-shipped tenant metrics.

## 2. Non-Negotiable Boundaries

### B1. Physical capacity and logical tenant usage are separate numbers

Required wording and implementation model:

- **Physical content-store capacity**: total/used/free bytes of the filesystem backing `ecm.storage.root-path`, after global dedup.
- **Tenant logical usage**: sum of per-tenant `storageUsedBytes` from the frozen ADR-002 model.

Forbidden:

- Do not derive physical store usage by summing tenant logical usage.
- Do not derive tenant usage by walking physical files.
- Do not present `totalStorageUsed` as physical disk/store usage.

### B2. Do not assume MinIO/S3 for Athena Day-1

Yuantus #828 was a MinIO symptom caused by host disk exhaustion. That lesson is useful operationally, but Athena's content write path is not a MinIO client path.

Required:

- First build for Athena's current filesystem-backed `ContentService`.
- If a future storage adapter or S3 client lands, treat that as a new taskbook.

### B3. Extend current metrics surface

Required:

- Backend: add one read-only admin endpoint or DTO surface for physical content-store capacity.
- Frontend: add one card/section to `TenantMetricsDashboardPage`.
- Reuse existing tenant metric endpoints for logical usage.

Forbidden:

- Do not create a separate dashboard for Day-2.
- Do not add quota mutations, tenant plan changes, or storage routing changes.

### B4. Quota model is frozen and read-only

ADR-002's 2026-06-01 / 2026-06-02 addenda froze the quota model:

`usedBytes = live Document.fileSize under tenant root + non-current retained Version.fileSize under tenant root`

Required:

- Treat quota metrics as read-only observability.
- Keep enforcement unchanged.
- Keep ADR-001 global dedup unchanged.

Forbidden:

- Do not reopen logical-vs-physical tenant quota semantics.
- Do not change upload blocking behavior in this line.

## 3. Proposed Day-2 Build Slice

### 3.1 Backend DTO

Introduce a read-only DTO, for example:

```java
public record StorageCapacityStatus(
    String backendType,
    String status,
    long totalBytes,
    long usableBytes,
    long usedBytes,
    double usedPercent,
    int warnPercent,
    int criticalPercent,
    long blockedMinFreeBytes,
    String rootPath,
    String error
) {}
```

Notes:

- `backendType` should be `filesystem` for this slice.
- `status` should be one of `OK`, `WARN`, `CRITICAL`, `BLOCKED`, `UNKNOWN`.
- `rootPath` can be returned only if the existing admin audience is acceptable for path disclosure; otherwise return a redacted or labeled path. Decide before implementation.
- `error` should be null unless the path cannot be inspected.

### 3.2 Backend service

Add a narrow `StorageCapacityService`:

- Reads `ecm.storage.root-path`.
- Normalizes it to a `Path`.
- Ensures the probe works even when the directory has not been created yet, by checking the nearest existing parent or creating only if existing code already does so safely. Preferred Day-2 behavior: no write side effects; inspect nearest existing parent and report `UNKNOWN` if no parent exists.
- Uses `Files.getFileStore(path)` for `totalBytes` and `usableBytes`.
- Computes `usedBytes = totalBytes - usableBytes` with floor-at-zero protection.
- Applies thresholds:
  - WARN when used percent >= 80.
  - CRITICAL when used percent >= 95.
  - BLOCKED when usable bytes <= configured minimum free bytes, or inspection proves the store is unusable.

Configuration names proposed:

```yaml
ecm:
  storage:
    capacity:
      warn-percent: 80
      critical-percent: 95
      blocked-min-free-bytes: 104857600
```

Open decision: whether `BLOCKED` is observability-only for this line or later becomes upload enforcement. Day-2 recommendation: observability-only.

### 3.3 Backend endpoint

Add an admin endpoint near the current tenant metrics namespace:

```text
GET /api/admin/storage/capacity
GET /api/v1/admin/storage/capacity
```

Rationale:

- It is global physical store state, not per-tenant.
- Keeping it under `/admin` matches the current `TenantAdminController` convention.
- It avoids mixing global capacity into each tenant's `TenantMetrics`.

### 3.4 Frontend integration

Extend `TenantMetricsDashboardPage`:

- Fetch `tenantService.getStorageCapacity()` alongside `listTenants()` / `getTenantMetrics`.
- Rename the current summary card from `Total Storage Used` to `Tenant Logical Usage`.
- Add a `Physical Content Store` card showing status, used/free/total, and used percent.
- Add a short caption that the physical card is deduped store capacity while tenant usage is logical quota usage.

No new dashboard. No tenant quota edit UI.

## 4. Tests

### Backend tests

Add focused tests for `StorageCapacityService`:

- Returns `OK` for a temp directory with normal free space.
- Computes used/free/total consistently and never returns negative `usedBytes`.
- Returns `WARN` / `CRITICAL` by overriding thresholds to low values in a temp-path test.
- Returns `UNKNOWN` with a sanitized error for an uninspectable or missing path case.
- Does not require or instantiate MinIO/S3 clients.

Add controller/security coverage:

- Admin endpoint returns the DTO shape.
- Non-admin access follows existing admin controller security conventions.

### Frontend tests

Add or extend tests for:

- `tenantService.getStorageCapacity()` response-shape guard.
- `TenantMetricsDashboardPage` renders both physical capacity and logical usage labels.
- Capacity fetch failure does not break tenant metrics rendering; it shows an operator-friendly warning.

## 5. Verification Plan

Local targeted commands:

```bash
cd ecm-core
./mvnw -q -Dtest=StorageCapacityServiceTest,TenantAdminControllerTest test
```

```bash
cd ecm-frontend
npm test -- --watchAll=false TenantMetricsDashboardPage tenantService
```

CI expectation:

- Backend Verify
- Frontend Build & Test
- Frontend E2E Core Gate, if path filters include the changed frontend page
- Existing security/property gates should remain unaffected

Manual smoke, if a docker stack is available:

```bash
curl -sS http://localhost:8080/api/admin/storage/capacity
```

Expected smoke result:

- `backendType=filesystem`
- status one of `OK|WARN|CRITICAL|BLOCKED`
- numeric `totalBytes`, `usableBytes`, `usedBytes`, `usedPercent`
- no tenant logical sum presented as physical usage

## 6. Out of Scope

- S3 / MinIO SDK integration.
- Per-tenant buckets or per-tenant paths.
- Changing ADR-001 global dedup.
- Changing ADR-002 logical quota model.
- Upload blocking or write-path enforcement.
- Capacity prediction beyond threshold status.
- Volume pruning, host cleanup, or Docker ops automation.
- Per-tenant physical storage accounting.

## 7. Ratification Checklist

Before Day-2 code starts, confirm:

- [ ] Physical capacity should be read from the filesystem behind `ecm.storage.root-path`.
- [ ] The first endpoint can be admin read-only at `/api/admin/storage/capacity` and `/api/v1/admin/storage/capacity`.
- [ ] The thresholds are 80 / 95 / `blocked-min-free-bytes=100MiB` unless overridden.
- [ ] `BLOCKED` is observability-only in this line, not upload enforcement.
- [ ] The frontend change belongs on `TenantMetricsDashboardPage`, not a new dashboard.
- [ ] Existing tenant quota metrics remain logical and unchanged.

## 8. Recommendation

Ratify this Day-2 build slice as the next development item.

It gives operators the missing answer from the #828 class of failure — "is the actual content store nearly full?" — without disturbing the frozen quota model or pretending tenant logical usage equals physical bytes.
