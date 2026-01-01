# E2E Full Run Report (single worker)

## Summary
- Playwright config now defaults to 1 worker locally (override with `ECM_E2E_WORKERS`).
- Full E2E suite completed without failures.

## Command
```
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test
```

## Result
- 15 passed, 0 failed (7.1m).

## Notes
- PDF/preview and search flows required index polling fallback but completed successfully.
- Antivirus remained `enabled=true` and `available=false`; EICAR upload skipped per test logic.
