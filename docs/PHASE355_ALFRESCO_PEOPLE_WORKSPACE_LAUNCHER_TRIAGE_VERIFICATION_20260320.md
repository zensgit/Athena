# Phase 355 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/PeopleDirectoryPage.tsx
cd ecm-frontend && npm run -s build
```

## Result

- `PeopleDirectoryPage` eslint passed
- frontend production build passed

## Notes

- 本阶段无新增 backend contract。
- triage filter 只在 people workspace 已加载的数据上运行，不影响现有 quick actions 和 row-click 默认入口。
