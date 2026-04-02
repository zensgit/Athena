# Phase 357 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx
cd ecm-frontend && npm run -s build
```

## Result

- workflow page `eslint` passed
- frontend production build passed

## Notes

- 本阶段没有新增 backend contract
- 本阶段是在 Phase 356 的 activity server filter contract 之上，补齐 UI parity
