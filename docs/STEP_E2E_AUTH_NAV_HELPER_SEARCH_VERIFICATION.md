# Step: E2E Auth Navigation Helper Expansion (Search Specs) Verification

## Validation Command

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
    e2e/search-highlight.spec.ts \
    e2e/search-sort-pagination.spec.ts \
    e2e/search-view.spec.ts \
    --workers=1
```

## Result
- `4 passed`
- `0 failed`

## Verified Coverage
- Search highlight snippet scenario.
- Search sorting and pagination consistency scenario.
- Search preview visibility scenario.
- Search ACL isolation for viewer role scenario.

## Notes
- Verification was executed against local latest frontend source (`http://localhost:3000`) to ensure helper refactor is tested on current code.
