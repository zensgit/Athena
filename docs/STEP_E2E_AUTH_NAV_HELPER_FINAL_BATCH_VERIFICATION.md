# Step: E2E Auth Navigation Helper Expansion (Remaining Specs) Verification

## Validation Command

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
    e2e/version-share-download.spec.ts \
    e2e/pdf-preview.spec.ts \
    e2e/webhook-admin.spec.ts \
    e2e/rules-manual-backfill-validation.spec.ts \
    --workers=1
```

## Result
- `8 passed`
- `0 failed`

## Verified Coverage
- Version history: download / compare / restore.
- Share link controls: password / deactivation / access limits.
- PDF preview normal path + server fallback path + browse view entry.
- Webhook admin create/test/delete + event-type filter behavior.
- Rules UI validation for manual backfill range.

## Notes
- Validation executed against local latest frontend source (`http://localhost:3000`).
