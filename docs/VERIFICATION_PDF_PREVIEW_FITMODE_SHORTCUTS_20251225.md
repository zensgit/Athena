## PDF Preview Fit Mode Shortcuts Verification (2025-12-25)

### Scope
- Add keyboard shortcuts for fit modes and show shortcut hints in the fit menu.

### Build/Deploy
- `cd ecm-frontend && npm run build` (successful)

### Verification Steps
1. Code inspection:
   - Added key handlers: `F` (fit to screen), `H` (fit to height), `W` (fit to width), `0` (actual size).
   - Fit menu labels include shortcut hints (e.g., `Fit to height (H)`).

### Result
- Build succeeds with no eslint warnings.
- Shortcut behavior is implemented; runtime UI verification requires a running frontend build.

### Notes
- Docker rebuild not performed due to local Docker socket permission error; use `npm start` or rebuild container to validate in UI.
