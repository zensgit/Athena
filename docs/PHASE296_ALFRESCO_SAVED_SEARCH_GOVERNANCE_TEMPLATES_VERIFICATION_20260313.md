# Phase 296 - Alfresco 对标：Saved Search Governance Templates（Verification）

## Date
- 2026-03-13

## Verification Scope
- 验证 saved search 模板 API 可用、tag 过滤生效。
- 验证 Advanced Search 模板加载/应用功能不破坏现有检索流程。

## Automated Verification

### 1) Backend targeted tests
- Command:
```bash
cd ecm-core && mvn -Dtest=SavedSearchServiceTemplateTest,SavedSearchControllerTemplateTest test
```
- Result:
  - `BUILD SUCCESS`
  - `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

### 2) Frontend lint
- Command:
```bash
cd ecm-frontend && npm run lint -- src/pages/AdvancedSearchPage.tsx src/services/savedSearchService.ts
```
- Result:
  - exit code `0`
  - lint 通过

### 3) Frontend production build
- Command:
```bash
cd ecm-frontend && npm run build
```
- Result:
  - `Compiled successfully.`
  - 产物输出到 `ecm-frontend/build/`

## Contract Checks
- `GET /api/v1/search/saved/templates`
  - 返回内置模板列表。
- `GET /api/v1/search/saved/templates?tag=governance`
  - 返回含 `governance` tag 的模板子集。

## UI Checks
- `AdvancedSearchPage` 可展示 `Governance Search Templates`。
- 点击模板后可自动套用过滤器并触发一次检索。
- 模板加载失败时仅显示局部错误提示，不影响主搜索与过滤功能。

## Conclusion
- Phase296 功能与验证通过，可用于治理场景快速检索和运维排障入口标准化。
