# Phase 2 Execution Progress

Date: 2026-02-12

This file tracks execution against `docs/NEXT_7DAY_PLAN_20260212.md`.

## Day 1 (P0) - Mail Diagnostics Run ID + UI Surfacing

Status: DONE

Artifacts:
- Design: `docs/PHASE2_D1_MAIL_DIAGNOSTICS_RUNID_DESIGN_20260212.md`
- Verification: `docs/PHASE2_D1_MAIL_DIAGNOSTICS_RUNID_VERIFICATION_20260212.md`

Automation:
- Backend unit: `mvn -q test -Dtest=MailFetcherServiceDiagnosticsTest`
- Playwright E2E: `npx playwright test e2e/mail-automation.spec.ts -g "Mail automation test connection and fetch summary"`

Key changes:
- Backend adds `runId` to `MailFetchSummary` and `MailRulePreviewResult`, with correlation logs.
- Frontend surfaces `runId` chips (click-to-copy) for trigger fetch, debug run, and rule preview.

## Day 2 (P0) - Permission Template Diff Export JSON + Audit Event

Status: DONE

Artifacts:
- Design: `docs/PHASE2_D2_PERMISSION_TEMPLATE_DIFF_JSON_DESIGN_20260212.md`
- Verification: `docs/PHASE2_D2_PERMISSION_TEMPLATE_DIFF_JSON_VERIFICATION_20260212.md`

Automation:
- Backend unit: `mvn -q test -Dtest=PermissionTemplateServiceDiffTest,PermissionTemplateDiffExportControllerTest`
- Playwright E2E: `npx playwright test e2e/permission-templates.spec.ts -g "Admin can view permission template history"`

Key changes:
- Backend adds `GET /api/v1/security/permission-templates/{id}/versions/diff/export` supporting `format=json|csv` and logs `SECURITY_PERMISSION_TEMPLATE_DIFF_EXPORT`.
- Frontend adds `Export JSON` and routes both exports through the backend (ensures audit trail).

## Next

## Day 3 (P0) - Preview Retry Per-Item Actions (Search + Advanced Search)

Status: DONE

Artifacts:
- Design: `docs/PHASE2_D3_PREVIEW_RETRY_PER_ITEM_DESIGN_20260212.md`
- Verification: `docs/PHASE2_D3_PREVIEW_RETRY_PER_ITEM_VERIFICATION_20260212.md`

Automation:
- Playwright E2E: `npx playwright test e2e/search-preview-status.spec.ts`

Key changes:
- Search results and Advanced Search now expose per-item `Force rebuild preview` alongside `Retry preview` for retryable failures.
- UNSUPPORTED previews never show retry/rebuild actions.

## Next

- Day 4 (P1): Search snippet enrichment (Path + Creator + Match Fields).

## Day 4 (P1) - Search Snippet Enrichment (Path + Creator + Match Fields)

Status: DONE

Artifacts:
- Design: `docs/PHASE2_D4_SEARCH_SNIPPET_ENRICHMENT_DESIGN_20260212.md`
- Verification: `docs/PHASE2_D4_SEARCH_SNIPPET_ENRICHMENT_VERIFICATION_20260212.md`

Automation:
- Playwright E2E: `npx playwright test e2e/search-snippet-enrichment.spec.ts`

Key changes:
- Search Results and Advanced Search now show a breadcrumb-style path + `By {creator}` line and keep compact `Matched in` chips.

## Next

- Day 5 (P1): Mail reporting scheduled export (optional if time allows).

## Day 5 (P1) - Mail Reporting Scheduled Export

Status: DONE

Artifacts:
- Design: `docs/PHASE2_D5_MAIL_REPORT_SCHEDULE_EXPORT_DESIGN_20260212.md`
- Verification: `docs/PHASE2_D5_MAIL_REPORT_SCHEDULE_EXPORT_VERIFICATION_20260212.md`

Automation:
- Backend unit: `mvn -q test -Dtest=MailReportScheduledExportServiceTest`
- Playwright E2E: `npx playwright test e2e/mail-automation.spec.ts -g "Mail automation reporting panel renders"`

Key changes:
- Backend adds a scheduled export service that can upload the mail reporting CSV into a configured folder, plus status + manual trigger endpoints.
- Frontend surfaces a read-only "Scheduled export" status panel under Mail Reporting.

## Day 6 (Tech Debt) - Docs Index + Regression Gate Consolidation

Status: DONE

Artifacts:
- Index: `docs/DOCS_INDEX_20260212.md`
- Weekly regression commands: `docs/WEEKLY_REGRESSION_UPDATE_20260212.md`

Verification:
- Playwright core gate (selected specs): `28 passed`

Key changes:
- Added a curated docs index for Phase 1 + Phase 2 deliverables by topic (Mail/Search/Preview/Permissions/E2E).
- Consolidated a weekly regression command set (backend targeted tests + core/extended Playwright suites).

## Day 7 (Ops) - CI / Local Guardrails

Status: DONE

Artifacts:
- Design: `docs/PHASE2_D7_CI_GUARDRAILS_DESIGN_20260212.md`
- Verification: `docs/PHASE2_D7_CI_GUARDRAILS_VERIFICATION_20260212.md`

Automation:
- Smoke-only verify (guardrail + tokens + smoke): `./scripts/verify.sh --no-restart --smoke-only --skip-build --skip-wopi`
- Playwright spot-check: `npx playwright test e2e/mail-automation.spec.ts -g "Mail automation reporting panel renders"`

Key changes:
- Added `.env.mail` guardrails so `docker compose` and `scripts/verify.sh` do not fail on clean checkouts.
