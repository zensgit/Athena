# Phase 345 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/PeopleDirectoryPage.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/PeopleDirectoryPage.tsx
```

## Result

- `PeopleDirectoryPage` eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段不新增后端接口，全部是 people workspace 里的 row-level launcher 动作前移。
