# Phase 346 - Alfresco Workflow Variable Filter Parity

## Goal

把 workflow variables 的过滤能力补齐到两个主工作台，避免出现“任务页能筛、流程页不能筛”或“process variables 能筛、task variables 不能筛”的体验割裂。

## Scope

- `TasksPage`
  - `Task Variables` 增加本地过滤
  - 保持 `Workflow History` quick scopes 生效
- `WorkflowProcessesPage`
  - `Process Variables` 增加本地过滤
  - 保持 `Workflow History` quick scopes 生效

## Frontend Design

### Variable Filter Coverage

两个变量过滤器统一匹配：

- `name`
- `type`
- `scope`
- `value`

交互原则：

- 250ms debounce
- 有变量但无匹配时，显示明确 empty-state
- 不新增 backend query contract，全部本地过滤

### Workbench Parity

本阶段重点不是再加新接口，而是把已有 workflow 能力做成一致的、可预测的工作台体验：

- process browser 有 variable filter
- task workbench 也有 variable filter
- history quick scopes 在两页都保留

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow variable governance 已从“有能力”提升到“两个工作台都一致可用”，更适合真实运营和排障场景。
