# Phase 34 Full Frontend E2E (Verification)

## Command
```
cd ecm-frontend
npx playwright test
```

## Result
- ❌ 26 failed, 2 passed (28.4m)

## Failure summary (representative)
- Multiple specs failed at `loginWithCredentials` waiting for `/browse` redirect (timeout 60s).
- Affected suites include: `browse-acl`, `mail-automation`, `p1-smoke`, `pdf-preview`, `permissions-dialog`, `rules-manual-backfill-validation`, `search-*`, `ui-smoke`, `version-share-download`.

## Notes
- See `ecm-frontend/test-results/**/error-context.md` and `trace.zip` for each failure.
- Most errors show `page.waitForURL` timeouts after login, indicating auth/redirect did not complete (possible Keycloak/SSO or env readiness issue).
