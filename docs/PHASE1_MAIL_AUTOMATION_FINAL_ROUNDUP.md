# Mail Automation Phase-1 Final Roundup (P28-P32)

Date: 2026-02-06

## Scope Completed
- P28 Export traceability and filter/sort snapshot metadata.
- P29 Runtime observability endpoint + UI runtime health panel.
- P30 Mail-to-document/search linkage actions in diagnostics tables.
- P31 Permission UX hardening + stronger audit payload/events.
- P32 Regression suite expansion for new mail automation behavior.

## Key Delivered Outcomes
### Backend
- Diagnostics CSV export now includes request/actor/filter/sort metadata.
- Runtime metrics endpoint added:
  - `GET /api/v1/integration/mail/runtime-metrics`
- Audit coverage strengthened:
  - `MAIL_DIAGNOSTICS_EXPORTED` now logs request/sort/order/include flags.
  - `MAIL_RUNTIME_METRICS_VIEWED` added.

### Frontend
- Runtime Health panel added on Mail Automation page.
- Export scope snapshot visible before CSV export.
- Processed/Mail Documents tables now provide direct linkage actions:
  - open linked document
  - find similar documents
- Permission-denied (`403`) user feedback added for runtime/replay actions.

### E2E
- Added/updated cases in `ecm-frontend/e2e/mail-automation.spec.ts`:
  - replay failed item
  - runtime health panel rendering
  - export scope snapshot rendering
  - similar navigation action

## Verification Summary
- `cd ecm-core && mvn -DskipTests compile`
  - pass
- `cd ecm-frontend && npm run lint`
  - pass
- `cd ecm-frontend && npx playwright test e2e/mail-automation.spec.ts --reporter=line`
  - blocked by execution environment:
    - default run expected missing `mac-x64` headless shell path.
    - forced install to isolated path failed due DNS resolution errors to Playwright CDN hosts.
    - fallback arm64 launch failed with macOS process permission errors (`MachPortRendezvous` / `crashpad bootstrap_check_in`).

## Related Design/Verification Docs
- `docs/PHASE1_P28_EXPORT_TRACEABILITY_DESIGN.md`
- `docs/PHASE1_P28_EXPORT_TRACEABILITY_VERIFICATION.md`
- `docs/PHASE1_P29_MAIL_RUNTIME_OBSERVABILITY_DESIGN.md`
- `docs/PHASE1_P29_MAIL_RUNTIME_OBSERVABILITY_VERIFICATION.md`
- `docs/PHASE1_P30_MAIL_TO_DOCUMENT_LINKAGE_DESIGN.md`
- `docs/PHASE1_P30_MAIL_TO_DOCUMENT_LINKAGE_VERIFICATION.md`
- `docs/PHASE1_P31_MAIL_PERMISSION_AUDIT_HARDENING_DESIGN.md`
- `docs/PHASE1_P31_MAIL_PERMISSION_AUDIT_HARDENING_VERIFICATION.md`
- `docs/PHASE1_P32_MAIL_AUTOMATION_REGRESSION_DESIGN.md`
- `docs/PHASE1_P32_MAIL_AUTOMATION_REGRESSION_VERIFICATION.md`
