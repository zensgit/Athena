# Frontend Service Guard Inventory

Date: 2026-05-21

## Executive Summary

Across `ecm-frontend/src/services/*.ts` (50 files; `api.ts` and `authBootstrap.ts` excluded as non-service), every service that returns JSON has a runtime response-shape guard.

- **47 services** in scope (nodeService skipped — fully closed per
  `docs/NODE_SERVICE_RESPONSE_SHAPE_GUARDS_CLOSEOUT_20260521.md`).
- **44 fully guarded** using the canonical
  `api.<verb><unknown>` + `assertResponse`/`assertResponseArray`/
  `assertX*` pattern.
- **1 fully guarded with a stylistic deviation** (`siteInvitationService` —
  uses `api.<verb><SiteInvitationDto>` instead of `<unknown>`, but
  every method still calls a real `assertSiteInvitationDto` /
  `assertSiteInvitationArray` predicate at runtime).
- **1 fully guarded for its JSON surface, plus 1 Blob method that is OOS by design** (`opsRecoveryService` — 25 JSON methods all use the canonical pattern; 1 `api.post<Blob>` for CSV export which is intentionally outside guard scope).
- **1 service with no JSON methods** (`authService` — only token/bootstrap calls, all return `Promise<void>`).
- **0 services have un-asserted JSON methods.**

**There is no meaningful "Top 3 next slice" recommendation, because there is no next slice.** The frontend service guard backlog is empty.

## Detection method (why this inventory disagrees with the earlier scan)

A first-pass Explore agent reported 45 services as "partially guarded" with ~342 un-guarded JSON methods. That was incorrect: the agent counted methods using `api.<verb><Typed>` as "un-guarded" without checking whether each call site also runs a downstream `assert*` predicate. A direct grep against the actual conversion pattern (`api.<verb><unknown>` and `assert*` calls per file) gives the authoritative count below.

For this doc, a method is **guarded** if it either:

1. Calls `api.<verb><unknown>` and immediately runs `assertResponse` / `assertResponseArray` / `assertPageResponse` / a per-DTO `assert<DtoName>` helper, OR
2. Calls `api.<verb><DtoName>` and immediately runs the corresponding `assert<DtoName>` helper (functionally identical at runtime — the predicate fires either way).
3. Calls `api.postFormData<unknown>` and immediately runs the corresponding
   `assert<DtoName>` helper.

A method is **OOS** if it returns `Promise<void>`, `Promise<Blob>`, or routes
through `api.downloadFile` / `api.getBlob` / `api.uploadFile`.

## Per-service table

| Service | JSON methods | api<unknown> | api<Typed>* | Blob/download OOS | Promise<void> | Has sentinel | Tier |
|---|--:|--:|--:|--:|--:|:--:|:--:|
| activityService | 5 | 5 | 0 | 0 | 0 | Y | D |
| blogService | 7 | 7 | 0 | 0 | 1 | Y | D |
| bulkImportService | 4 | 4 | 0 | 0 | 0 | Y | D |
| bulkMetadataService | 1 | 1 | 0 | 0 | 0 | Y | D |
| bulkOperationService | 4 | 4 | 0 | 3 | 3 | Y | D |
| calendarService | 5 | 5 | 0 | 0 | 1 | Y | D |
| categoryService | 4 | 4 | 0 | 0 | 3 | Y | D |
| cmisService | 3 | 3 | 0 | 0 | 0 | Y | D |
| commentService | 8 | 8 | 0 | 0 | 3 | Y | D |
| contentArchiveService | 10 | 10 | 0 | 0 | 1 | Y | D |
| contentModelService | 13 | 13 | 0 | 0 | 5 | Y | D |
| contentTypeService | 4 | 4 | 0 | 0 | 2 | Y | D |
| correspondentService | 3 | 3 | 0 | 0 | 0 | Y | D |
| dictionaryService | 8 | 8 | 0 | 0 | 0 | Y | D |
| discussionService | 7 | 7 | 0 | 0 | 2 | Y | D |
| dispositionScheduleService | 7 | 7 | 0 | 0 | 1 | Y | D |
| favoriteService | 3 | 3 | 0 | 0 | 2 | Y | D |
| followingService | 3 | 3 | 0 | 0 | 1 | Y | D |
| ldapService | 2 | 2 | 0 | 0 | 0 | Y | D |
| legalHoldService | 6 | 6 | 0 | 0 | 0 | Y | D |
| localizedContentService | 3 | 3 | 0 | 0 | 1 | Y | D |
| mailAutomationService | 26 | 26 | 0 | 2 | 2 | Y | D |
| mlService | 5 | 5 | 0 | 0 | 0 | Y | D |
| notificationService | 5 | 5 | 0 | 0 | 1 | Y | D |
| oauthCredentialAdminService | 6 | 6 | 0 | 0 | 0 | Y | D |
| opsPolicyService | 4 | 4 | 0 | 0 | 0 | Y | D |
| opsRecoveryService | 25 | 25 | 0 (1 Blob) | 8 | 7 | Y | D |
| peopleService | 27 | 27 | 0 | 0 | 3 | Y | D |
| permissionTemplateService | 6 | 6 | 0 | 1 | 2 | Y | D |
| previewDiagnosticsService | 61 | 61 | 0 | 12 | 13 | Y | D |
| propertyEncryptionService | 12 | 12 | 0 | 0 | 0 | Y | D |
| ratingService | 4 | 4 | 0 | 0 | 1 | Y | D |
| recordsManagementService | 37 | 37 | 0 | 6 | 10 | Y | D |
| ruleService | 22 | 22 | 0 | 2 | 4 | Y | D |
| savedSearchService | 8 | 8 | 0 | 0 | 1 | Y | D |
| scriptService | 5 | 5 | 0 | 0 | 1 | Y | D |
| shareLinkService | 8 | 8 | 0 | 0 | 2 | Y | D |
| siteInvitationService | 5 | 0 | 5 (DTO + assert) | 0 | 1 | Y | D* |
| siteService | 11 | 11 | 0 | 0 | 3 | Y | D |
| tagService | 7 | 7 | 0 | 0 | 5 | Y | D |
| templateService | 5 | 5 | 0 | 0 | 1 | Y | D |
| tenantService | 6 | 6 | 0 | 0 | 1 | Y | D |
| transferReplicationService | 14 | 14 | 0 | 0 | 3 | Y | D |
| trashService | 2 | 2 | 0 | 0 | 2 | Y | D |
| userGroupService | 6 | 6 | 0 | 0 | 3 | Y | D |
| workflowService | 23 | 23 | 0 | 2 | 0 | Y | D |
| authService | 0 | 0 | 0 | 0 | 3 | N | — (no JSON) |

`*` In the `api<Typed>` column the count counts only TS-generic-typed calls that *also* run a downstream `assert*` predicate; the `siteInvitationService` row is the only one where this column is non-zero, and the 5 sites are still guarded.

`bulkImportService.startImport` uses `api.postFormData<unknown>` and immediately
asserts the JSON response with `assertImportJobDto`, so it is counted as a
guarded JSON method rather than a blob/upload OOS method.

`Tier D` = fully runtime-guarded for all JSON methods. `Tier D*` = same, with stylistic deviation noted.

## Anomalies worth mentioning (none is a guard gap)

### siteInvitationService — stylistic deviation only
All 5 methods follow the pattern:

```ts
const result = await api.get<SiteInvitationDto[]>('/sites/.../invitations');
return assertSiteInvitationArray(result);          // real predicate at line 57
```

Functionally identical to the canonical `api.get<unknown>` + `assertSiteInvitationArray` shape; the runtime guard still fires. Converting to the canonical idiom is a < 10-line cleanup, *optional*, with no behavior change.

### opsRecoveryService — 1 Blob method, intentionally OOS
Line 1071: `api.post<Blob>('/ops/recovery/dry-run/export', payload, { responseType: 'blob' })`. CSV export, declared `Promise<Blob>`. Out of guard scope by the standing OOS rule (Blob/download/upload/void). All 25 JSON-returning methods in this file use the canonical guarded pattern.

### authService — no JSON surface
3 methods total, all `Promise<void>` for token/session bootstrap. No JSON shape to guard.

## Next-step recommendation

**There is no "Top 3 next slice" because there is no next slice.** The frontend service response-shape guard backlog is empty.

The only candidate for additional work — and it is purely optional — is a stylistic cleanup:

- **Optional, low-priority**: convert the 5 sites in `siteInvitationService` from `api.<verb><SiteInvitationDto>` to `api.<verb><unknown>` for uniformity with the rest of the codebase. ~10-line diff, no runtime change, no E2E risk. Could be folded into any future touch of that file rather than a standalone slice.

Beyond that, the recommended next phase is to move off the guard track entirely.

## Phase transition

Two artefacts now exist that close the response-shape guard chapter:

1. `docs/NODE_SERVICE_RESPONSE_SHAPE_GUARDS_CLOSEOUT_20260521.md` — closes the `nodeService` work (8 subdomains).
2. This document — closes the cross-service work for the remaining 47 services.

A short follow-up `FRONTEND_SERVICE_GUARD_TRACK_CLOSEOUT_<date>.md` could roll both into a single program-level closure if helpful, but functionally the work is finished.

The gate should now decide whether to open a new track entirely (different concern: e.g. backend response-shape contract testing, or a different defensive layer) rather than searching for additional service-guard work.
