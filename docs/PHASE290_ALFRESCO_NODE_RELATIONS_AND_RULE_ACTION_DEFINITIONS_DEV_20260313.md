# Phase 290 - Alfresco 对标：Node Relations + Rule Action Definitions（Dev）

## Date
- 2026-03-13

## Goal
- 对齐并增强 Alfresco 常见的两类可发现能力：
  - 节点关系可见性（parents/children/sources/targets/versions/rendition summary）。
  - 规则动作定义可发现性（action definitions for UI builder）。
- 在不破坏现有 API 的前提下，提供可直接被前端消费的结构化接口。

## Benchmark Alignment (Alfresco Surpass Track)
- Parity:
  - Node 关系摘要与关系列表查询能力。
  - Rule action type + params + constraints 的 discoverability。
- Surpass:
  - 关系摘要同时回传 preview/rendition readiness 信号。
  - action definition 显式返回 `supported`（含未实现动作 `EXECUTE_SCRIPT` 标注）。

## Implementation Scope

### Backend - Node Relations
- File:
  - `ecm-core/src/main/java/com/ecm/core/repository/DocumentRelationRepository.java`
  - `ecm-core/src/main/java/com/ecm/core/service/DocumentRelationService.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`
- 主要改动：
  - repository 新增 source/target + relationType 的分页查询方法。
  - service 新增 outgoing/incoming 关系分页能力（支持可选 relationType 过滤）。
  - node controller 新增关系接口：
    - `GET /api/v1/nodes/{nodeId}/relations/summary`
    - `GET /api/v1/nodes/{nodeId}/relations/parents`
    - `GET /api/v1/nodes/{nodeId}/relations/children`
    - `GET /api/v1/nodes/{nodeId}/relations/sources`
    - `GET /api/v1/nodes/{nodeId}/relations/targets`
    - `GET /api/v1/nodes/{nodeId}/relations/versions`
    - `GET /api/v1/nodes/{nodeId}/relations/renditions/summary`
  - 新增 DTO records：
    - `NodeRelationsSummaryDto`
    - `NodeRelationNodeRefDto`
    - `NodeRelationEdgeDto`
    - `NodeRenditionRelationSummaryDto`

### Backend - Rule Action Definitions
- File:
  - `ecm-core/src/main/java/com/ecm/core/controller/RuleController.java`
- 新增接口：
  - `GET /api/v1/rules/actions/definitions`
- 输出结构：
  - `actions[]` 每项包含：
    - `type`
    - `supported`
    - `requiredParams[]`
    - `optionalParams[]`
    - `constraints[]`
- 关键映射约束：
  - `RENAME`：`atLeastOneOf:newName,pattern`
  - `START_WORKFLOW`：`workflowKey=documentApproval requires approvers`
  - `EXECUTE_SCRIPT`：`supported=false`

### Frontend - Advanced Search 面板接入
- File:
  - `ecm-frontend/src/services/nodeService.ts`
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- 主要改动：
  - `nodeService` 新增并导出 `NodeRelationsSummary` 类型与 `getNodeRelationsSummary(nodeId)` 方法。
  - `AdvancedSearchPage` 选取当前结果中首个 `DOCUMENT` 作为代表文档加载关系摘要。
  - 新增 `Node Relations Summary` 面板（位于 `Search Stats` 与 `Preview Status` 之间）：
    - Chips: `Parents/Children/Sources/Targets/Versions/Rendition/Status`
    - 说明：`Based on representative document: <name>`
    - 失败降级：`Relations summary unavailable`
  - 增加请求序列号防并发覆盖（竞态保护）。

### Frontend - Rules 页面动作定义接入
- File:
  - `ecm-frontend/src/services/ruleService.ts`
  - `ecm-frontend/src/pages/RulesPage.tsx`
- 主要改动：
  - `ruleService` 新增 `getActionDefinitions()` 与类型：
    - `RuleActionDefinition`
    - `RuleActionDefinitionsResponse`
  - `RulesPage` 在加载规则列表时并行拉取 action definitions。
  - 规则编辑弹窗在 `Actions (JSON array)` 下展示 `Available Action Definitions`：
    - 展示动作类型、required params 与 constraints
    - unsupported 动作用 warning 样式高亮（如 `EXECUTE_SCRIPT`）

## Test Additions
- `ecm-core/src/test/java/com/ecm/core/controller/RuleControllerActionDefinitionsTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RuleControllerActionDefinitionsSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java`

## Compatibility / Risk
- 所有改动均为增量接口与增量 UI，不移除既有接口。
- 风险：
  - 关系摘要依赖 representative document，极端查询结果下可能无代表文档。
- 缓解：
  - 前端无代表文档时不发请求并展示降级文案，不影响主搜索流。
