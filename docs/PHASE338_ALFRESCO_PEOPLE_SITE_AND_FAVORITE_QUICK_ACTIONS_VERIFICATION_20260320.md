# Phase 338 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/PeopleDirectoryPage.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/PeopleDirectoryPage.tsx
```

## Result

- `PeopleDirectoryPage.tsx` eslint passed
- frontend production build passed
- `git diff --check` passed

## Notes

- 该批改动只扩展前端动作面，未引入新的 people backend endpoint。
