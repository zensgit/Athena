# Phase 291 - Alfresco 对标：Node Relation Details Panel + Endpoint Coverage（Dev）

## Date
- 2026-03-13

## Goal
- 在已上线的 Node Relations Summary 基础上，补齐“关系明细可见性”。
- 强化 Node relations 接口自动化覆盖，避免后续迭代回归。

## Scope

### Frontend
- Files:
  - `ecm-frontend/src/services/nodeService.ts`
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- 改动：
  - `nodeService` 新增关系明细类型与 API：
    - `NodeRelationNodeRef`
    - `NodeRelationEdge`
    - `getNodeRelationParents`
    - `getNodeRelationSources`
    - `getNodeRelationTargets`
    - `getNodeRelationVersions`
  - `AdvancedSearchPage` 的 `Node Relations Summary` 面板新增明细区：
    - Parents（path 列表）
    - Sources（relationType + source name）
    - Targets（relationType + target name）
    - Versions（versionLabel + creator）
  - 增加明细请求竞态保护（独立 request seq），并提供降级文案：
    - `Relations details unavailable`
    - `No relation details`

### Backend Tests
- File:
  - `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java`
- 新增覆盖：
  - `parents`：验证返回顺序为 root -> direct parent。
  - `targets`：验证 relationType/source/target 映射。
  - `versions`（folder）：验证空分页 + `versionService` 不被调用。

## Compatibility
- 本次为增量能力，不破坏现有接口与页面流程。
- 关系明细请求失败时，页面主搜索流不受影响。

