# Docs Index (2026-02-12)

This file is a curated index for the Phase 1 + Phase 2 work executed under `docs/NEXT_7DAY_PLAN_20260212.md`.

Goal: make it easy to find the latest design + verification artifacts by topic without having to grep the repo.

## Plan + Progress

- Plan: `docs/NEXT_7DAY_PLAN_20260212.md`
- Phase 3 plan: `docs/NEXT_7DAY_PLAN_PHASE3_20260212.md`
- Phase 1 progress: `docs/PHASE1_EXECUTION_PROGRESS_20260212.md`
- Phase 2 progress: `docs/PHASE2_EXECUTION_PROGRESS_20260212.md`
- Phase 3 progress: `docs/PHASE3_EXECUTION_PROGRESS_20260213.md`
- Phase 3 commit split: `docs/PHASE3_COMMIT_SPLIT_PLAN_20260213.md`
- Phase 3 release notes: `docs/RELEASE_NOTES_20260213_PHASE3.md`
- Phase 3 delivery report (single file): `docs/PHASE3_DELIVERY_REPORT_20260213.md`
- Phase 4 plan: `docs/NEXT_7DAY_PLAN_PHASE4_20260213.md`
- Phase 5 plan: `docs/NEXT_7DAY_PLAN_PHASE5_20260213.md`
- Phase 4 D1: Preview retry classification hardening: `docs/PHASE4_D1_PREVIEW_RETRY_CLASSIFICATION_20260213.md`
- Phase 4 D2: MIME type normalization (octet-stream): `docs/PHASE4_D2_MIME_TYPE_NORMALIZATION_20260213.md`
- Phase 4 D3: Preview failure taxonomy + UX messaging: `docs/PHASE4_D3_PREVIEW_FAILURE_TAXONOMY_UX_20260213.md`
- Phase 4 D4: Backend preview queue guardrails: `docs/PHASE4_D4_BACKEND_PREVIEW_QUEUE_GUARDRAILS_20260213.md`
- Phase 4 D5: Preview observability + diagnostics: `docs/PHASE4_D5_OBSERVABILITY_DIAGNOSTICS_20260213.md`
- Phase 4 D6: Automation coverage expansion: `docs/PHASE4_D6_AUTOMATION_COVERAGE_EXPANSION_20260213.md`
- Phase 4 D7: Regression gate + release notes: `docs/PHASE4_D7_REGRESSION_GATE_RELEASE_NOTES_20260213.md`

## OCR (Phase 3)

- Day 1: `ml-service` OCR endpoint + core OCR queue
  - Design: `docs/PHASE3_D1_OCR_ML_SERVICE_DESIGN_20260212.md`
  - Verification: `docs/PHASE3_D1_OCR_ML_SERVICE_VERIFICATION_20260212.md`
- Day 2: API-level OCR end-to-end smoke
  - Design: `docs/PHASE3_D2_OCR_E2E_SMOKE_DESIGN_20260212.md`
  - Verification: `docs/PHASE3_D2_OCR_E2E_SMOKE_VERIFICATION_20260212.md`
- Day 3: OCR status chip + queue actions in preview UI
  - Design: `docs/PHASE3_D3_OCR_UI_STATUS_DESIGN_20260212.md`
  - Verification: `docs/PHASE3_D3_OCR_UI_STATUS_VERIFICATION_20260212.md`
- Day 4: OCR-driven correspondent enrichment (feature-flagged)
  - Design: `docs/PHASE3_D4_OCR_ENRICHMENT_CORRESPONDENT_DESIGN_20260212.md`
  - Verification: `docs/PHASE3_D4_OCR_ENRICHMENT_CORRESPONDENT_VERIFICATION_20260212.md`
- Day 5: Redis-backed queue backend (preview + OCR)
  - Design: `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_DESIGN_20260212.md`
  - Verification: `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_VERIFICATION_20260212.md`
- Day 6: Rule dry-run and manual backfill guardrails
  - Design: `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_DESIGN_20260213.md`
  - Verification: `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_VERIFICATION_20260213.md`
- Day 7: Full regression gate and closeout
  - Design: `docs/PHASE3_D7_FULL_REGRESSION_GATE_DESIGN_20260213.md`
  - Verification: `docs/PHASE3_D7_FULL_REGRESSION_GATE_VERIFICATION_20260213.md`

## Mail Automation

- Phase 1
  - P101: Mail reporting selected-range empty-state clarity
    - Design: `docs/PHASE1_P101_MAIL_REPORTING_RANGE_CLARITY_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P101_MAIL_REPORTING_RANGE_CLARITY_VERIFICATION_20260212.md`
- Phase 2
  - D1: Diagnostics `runId` + UI surfacing
    - Design: `docs/PHASE2_D1_MAIL_DIAGNOSTICS_RUNID_DESIGN_20260212.md`
    - Verification: `docs/PHASE2_D1_MAIL_DIAGNOSTICS_RUNID_VERIFICATION_20260212.md`
  - D5: Mail reporting scheduled export (status + manual trigger + upload)
    - Design: `docs/PHASE2_D5_MAIL_REPORT_SCHEDULE_EXPORT_DESIGN_20260212.md`
    - Verification: `docs/PHASE2_D5_MAIL_REPORT_SCHEDULE_EXPORT_VERIFICATION_20260212.md`
- Phase 5
  - Mocked regression gate coverage (Trigger Fetch + last fetch summary)
    - Rollup: `docs/PHASE5_REGRESSION_GATE_ROLLUP_20260214.md`

## Permissions

- Phase 2
  - D2: Permission template diff export JSON + audit trail
    - Design: `docs/PHASE2_D2_PERMISSION_TEMPLATE_DIFF_JSON_DESIGN_20260212.md`
    - Verification: `docs/PHASE2_D2_PERMISSION_TEMPLATE_DIFF_JSON_VERIFICATION_20260212.md`
- Phase 5
  - Phase 56: Permission-set UX parity (Alfresco-style presets)
    - Dev: `docs/PHASE56_PERMISSION_SET_UX_PARITY_DEV_20260214.md`
    - Verification: `docs/PHASE56_PERMISSION_SET_UX_PARITY_VERIFICATION_20260214.md`

## Preview

- Phase 1
  - P100: Preview governance parity
    - Design: `docs/PHASE1_P100_PREVIEW_GOVERNANCE_PARITY_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P100_PREVIEW_GOVERNANCE_PARITY_VERIFICATION_20260212.md`
  - P103: Backend preview status canonicalization
    - Design: `docs/PHASE1_P103_BACKEND_PREVIEW_STATUS_CANONICALIZATION_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P103_BACKEND_PREVIEW_STATUS_CANONICALIZATION_VERIFICATION_20260212.md`
  - P106: Preview status facet counts from full-result aggregations
    - Design: `docs/PHASE1_P106_PREVIEW_STATUS_FACET_FULL_COUNTS_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P106_PREVIEW_STATUS_FACET_FULL_COUNTS_VERIFICATION_20260212.md`
- Phase 2
  - D3: Per-item preview retry / force rebuild actions (Search + Advanced Search)
    - Design: `docs/PHASE2_D3_PREVIEW_RETRY_PER_ITEM_DESIGN_20260212.md`
    - Verification: `docs/PHASE2_D3_PREVIEW_RETRY_PER_ITEM_VERIFICATION_20260212.md`
- Phase 4
  - D4: Backend preview queue guardrails
    - Design + verification: `docs/PHASE4_D4_BACKEND_PREVIEW_QUEUE_GUARDRAILS_20260213.md`
- Phase 5
  - Phase 54: Admin preview diagnostics UI
    - Dev: `docs/PHASE54_PREVIEW_DIAGNOSTICS_UI_DEV_20260213.md`
    - Verification: `docs/PHASE54_PREVIEW_DIAGNOSTICS_UI_VERIFICATION_20260213.md`
  - Phase 55: Preview diagnostics hardening (deep links + copy id + open parent folder)
    - Dev: `docs/PHASE55_PREVIEW_DIAGNOSTICS_HARDENING_DEV_20260214.md`
    - Verification: `docs/PHASE55_PREVIEW_DIAGNOSTICS_HARDENING_VERIFICATION_20260214.md`

## Version Management

- Version paging/history slice
  - Dev: `docs/PHASE15_VERSION_PAGED_HISTORY_DEV_20260203.md`
  - Verification: `docs/PHASE15_VERSION_PAGED_HISTORY_VERIFICATION_20260203.md`
- Phase 5 D5 (Phase 58): Version history paging + major-only toggle (mocked coverage)
  - Design: `docs/PHASE58_VERSION_HISTORY_PAGING_UX_DEV_20260214.md`
  - Verification: `docs/PHASE58_VERSION_HISTORY_PAGING_UX_VERIFICATION_20260214.md`

## Audit

- Audit execution history: `docs/EXECUTION_AUDIT_REPORT.md`
- Recent audit export fix: `docs/DESIGN_AUDIT_EXPORT_NODEID_NULL_UUID_FIX_20260210.md`
- Phase 5 D4 (Phase 57): Audit filtered explorer + stable export UX (Admin Dashboard)
  - Design: `docs/PHASE57_AUDIT_FILTER_EXPORT_UX_DEV_20260214.md`
  - Verification: `docs/PHASE57_AUDIT_FILTER_EXPORT_UX_VERIFICATION_20260214.md`

## Search (Simple Search + Advanced Search + Saved Search)

- Phase 5 D6 (Phase 59): Search spellcheck suggestions + Save Search convenience (mocked coverage)
  - Design: `docs/PHASE59_SEARCH_SUGGESTIONS_SAVED_SEARCH_UX_DEV_20260214.md`
  - Verification: `docs/PHASE59_SEARCH_SUGGESTIONS_SAVED_SEARCH_UX_VERIFICATION_20260214.md`
- Search continuity + regression gate
  - P104: Search continuity regression gate
    - Verification: `docs/PHASE1_P104_SEARCH_CONTINUITY_REGRESSION_GATE_VERIFICATION_20260212.md`
- Search fallback governance parity
  - P105: Criteria-key parity fix (fallback governance stays correct when query changes)
    - Design: `docs/PHASE1_P105_SEARCH_FALLBACK_CRITERIA_KEY_PARITY_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P105_SEARCH_FALLBACK_CRITERIA_KEY_PARITY_VERIFICATION_20260212.md`
- Saved search parser + compatibility hardening
  - P97: Saved-search JSON filter alias normalization
    - Design: `docs/PHASE1_P97_SAVED_SEARCH_JSON_FILTER_ALIAS_NORMALIZATION_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P97_SAVED_SEARCH_JSON_FILTER_ALIAS_NORMALIZATION_VERIFICATION_20260212.md`
  - P98: URL prefill fallback (SearchDialog)
    - Design: `docs/PHASE1_P98_SEARCH_DIALOG_URL_PREFILL_FALLBACK_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P98_SEARCH_DIALOG_URL_PREFILL_FALLBACK_VERIFICATION_20260212.md`
  - P99: Saved-search parser resilience
    - Design: `docs/PHASE1_P99_SAVED_SEARCH_PARSER_RESILIENCE_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P99_SAVED_SEARCH_PARSER_RESILIENCE_VERIFICATION_20260212.md`
- Advanced Search parity slice (P84-P96)
  - Active criteria summary
    - Design: `docs/PHASE1_P90_ADVANCED_SEARCH_ACTIVE_CRITERIA_SUMMARY_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P90_ADVANCED_SEARCH_ACTIVE_CRITERIA_SUMMARY_VERIFICATION_20260212.md`
  - Saved search load prefill parity
    - Design: `docs/PHASE1_P85_SAVED_SEARCH_LOAD_PREFILL_PARITY_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P85_SAVED_SEARCH_LOAD_PREFILL_PARITY_VERIFICATION_20260212.md`
  - More Advanced Search parity docs (filters/aspects/custom properties/legacy compat):
    - `docs/PHASE1_P84_ADVANCED_SEARCH_DIALOG_VALIDATION_HINT_*_20260212.md`
    - `docs/PHASE1_P86_SEARCH_FALLBACK_LAST_RETRY_OBSERVABILITY_*_20260212.md`
    - `docs/PHASE1_P87_ADVANCED_SEARCH_ASPECTS_CUSTOM_PROPERTIES_PARITY_*_20260212.md`
    - `docs/PHASE1_P88_SAVED_SEARCH_LEGACY_QUERYPARAMS_COMPAT_*_20260212.md`
    - `docs/PHASE1_P89_ADVANCED_SEARCH_PREFILL_AUTO_EXPAND_*_20260212.md`
    - `docs/PHASE1_P91_ADVANCED_SEARCH_PREFILL_SELECT_FALLBACK_*_20260212.md`
    - `docs/PHASE1_P92_SEARCH_RESULTS_ADVANCED_PREFILL_*_20260212.md`
    - `docs/PHASE1_P93_GLOBAL_ADVANCED_PREFILL_FROM_LAST_SEARCH_*_20260212.md`
    - `docs/PHASE1_P94_ADVANCED_PAGE_URL_TO_GLOBAL_PREFILL_*_20260212.md`
    - `docs/PHASE1_P95_SAVED_SEARCH_LEGACY_ALIAS_COMPAT_*_20260212.md`
    - `docs/PHASE1_P96_SAVED_SEARCH_STRING_NORMALIZATION_*_20260212.md`

## E2E + Tooling Guardrails

- Phase 1
  - P102: E2E target guardrail (`:3000` dev vs `:5500` prebuilt)
    - Design: `docs/PHASE1_P102_E2E_TARGET_GUARDRAIL_DESIGN_20260212.md`
    - Verification: `docs/PHASE1_P102_E2E_TARGET_GUARDRAIL_VERIFICATION_20260212.md`
- Phase 2
  - D7: Ops/CI guardrails
    - Design: `docs/PHASE2_D7_CI_GUARDRAILS_DESIGN_20260212.md`
    - Verification: `docs/PHASE2_D7_CI_GUARDRAILS_VERIFICATION_20260212.md`
- Phase 5
  - Regression gate rollup (mocked-first)
    - Doc: `docs/PHASE5_REGRESSION_GATE_ROLLUP_20260214.md`
    - Script: `scripts/phase5-regression.sh`
- Weekly regression command rollup:
  - `docs/WEEKLY_REGRESSION_UPDATE_20260212.md`

## References (Benchmark + Backlog)

- Alfresco comparison: `docs/ALFRESCO_GAP_ANALYSIS_20260129.md`
- Next iteration rollup: `docs/ROLLUP_NEXT_ITERATION_SUGGESTIONS_20260205.md`

## WOPI (Collabora/OnlyOffice)

- Integration overview: `docs/INTEGRATION_WOPI.md`
- Verification design reference: `docs/DESIGN_WOPI_VERIFY_AUTO_UPLOAD_20260106.md`
