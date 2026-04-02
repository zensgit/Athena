# Phase 343 - Verification

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

- 本阶段不新增后端接口，全部是前端协作入口前移和交互统一。
