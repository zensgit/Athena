# Mail Automation P28 — Verification

Date: 2026-02-06

## Automated Validation
- `cd ecm-core && mvn -DskipTests compile`
  - Result: pass
- `cd ecm-frontend && npm run lint`
  - Result: pass

## E2E Status
- `cd ecm-frontend && npx playwright test e2e/mail-automation.spec.ts`
  - Blocked in current environment:
    - default run expected `mac-x64` headless shell path while cache had `mac-arm64` binary.
    - retry install to isolated browsers path failed due DNS (`ENOTFOUND cdn.playwright.dev`).
    - fallback run with `PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=mac15-arm64` launches binary, but browser exits with macOS permission failure (`MachPortRendezvous ... Permission denied (1100)`).

## Manual Checklist
1. Open `/admin/mail#diagnostics`.
2. Apply diagnostics filters and sort.
3. Confirm `Export scope snapshot` text changes with filters.
4. Click `Export CSV`.
5. Validate CSV header rows include request/actor/filter/sort metadata.
