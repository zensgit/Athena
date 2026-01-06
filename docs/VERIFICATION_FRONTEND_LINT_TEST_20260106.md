# Verification: Frontend Lint + Test Run (2026-01-06)

- `cd ecm-frontend && npm run lint`
- `cd ecm-frontend && CI=true npm test -- --watchAll=false`
- Result: pass (3 test suites, 5 tests). React Router future-flag warnings emitted during tests.
