# Phase 213 - Preview Rendition Resources Export API - Verification

## Date
2026-03-08

## Scope
- Verify admin-only security and CSV response contract for rendition resources export endpoint.
- Verify backend compile stability after export API additions.

## Commands and results

1. Backend security/controller test
```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

## Verified outcomes
- Non-admin access to `/api/v1/preview/diagnostics/renditions/resources/export` is denied.
- Admin can export CSV with expected headers/content and audit event emission.
