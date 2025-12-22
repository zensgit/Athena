# Preview Stability Step 2 Design: PDF Fallback E2E Coverage

## Goal
Provide an end-to-end check that the PDF preview UI falls back to server-rendered images when the client PDF renderer fails.

## Approach
- Add a Playwright test that blocks the PDF worker asset to force a client render failure.
- Verify the fallback DOM (`[data-testid="pdf-preview-fallback"]`) renders an image.

## Scope
- `ecm-frontend/e2e/pdf-preview.spec.ts`

## Success Criteria
- The fallback test passes and renders the server preview image.
- No regression to the standard PDF preview test.

## Rollback
- Remove the fallback test block in `pdf-preview.spec.ts`.
