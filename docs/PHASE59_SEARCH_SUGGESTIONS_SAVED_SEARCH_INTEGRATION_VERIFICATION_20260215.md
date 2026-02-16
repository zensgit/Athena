# Phase 59 - Search Suggestions + Save Search (Integration Smoke) - Verification

## Date
2026-02-15

## Command

```bash
ECM_UI_URL=http://localhost \
ECM_API_URL=http://localhost:7700 \
bash scripts/phase5-search-suggestions-integration-smoke.sh
```

## Result
- PASS

## Verified
1. Advanced Search can save criteria as a Saved Search with real backend.
2. Saved search payload persists misspelled query for replay.
3. If spellcheck endpoint returns suggestion data, search results page renders "Did you mean" for misspelled query.
4. If suggestion is present, clicking it updates quick-search value to corrected query and shows results.

## Notes
- This complements mocked coverage in `e2e/search-suggestions-save-search.mock.spec.ts`.
- Use full-stack target (`http://localhost` or dev server) instead of static `:5500`.
- CI coverage is enforced in `.github/workflows/ci.yml` under job `Frontend E2E Core Gate`.
