# Phase 241 - Preview Preflight Resolver + Hash-Enforced Read Repair (Verification)

Date: 2026-03-10  
Scope: `ecm-core` + `ecm-frontend`

## 1. Backend Verification

## 1.1 Targeted feature tests

Command:

```bash
cd ecm-core
mvn -q -Dtest=PreviewPreflightResolverTest,SearchControllerTest,SearchControllerSecurityTest,DocumentControllerPreviewRepairTest test
```

Result: PASS

## 1.2 Regression set (preview/search/ops)

Command:

```bash
cd ecm-core
mvn -q -Dtest=PreviewPreflightResolverTest,SearchControllerTest,SearchControllerSecurityTest,DocumentControllerPreviewRepairTest,PreviewDeadLetterRegistryTest,PreviewQueueServiceTest,PreviewDiagnosticsControllerSecurityTest,OpsRecoveryControllerSecurityTest test
```

Result: PASS

Validated:

- preflight decline/accept semantics and skip counters
- search dry-run and async export regression behavior
- hash-enforced preview read path + repair endpoint behavior
- existing dead-letter/recovery/search preview regression paths

## 2. Frontend Verification

## 2.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts
```

Result: PASS

## 2.2 Production build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result: PASS

## 3. Acceptance Checklist

- [x] Preflight resolver added with route/capability/size/policy/pipeline outputs.
- [x] Dry-run skip breakdown includes preflight decline reasons.
- [x] Dry-run sample/CSV surfaces preflight diagnostics.
- [x] Preview read path enforces hash-safe stale handling.
- [x] Manual repair endpoint available (`/documents/{id}/preview/repair`).
- [x] Backend targeted + regression tests passing.
- [x] Frontend lint + build passing.
