# Phase 341 - Verification

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

- 该阶段不新增 backend contract，全部基于既有 people/comment/preview/favorite 能力做 UI 聚合与快捷动作前移。
