# Athena ECM Sprint 1–4 完成度核对（基于代码与可复跑验证）

日期：2025-12-17

本文件用于回答“Sprint 1/2/3/4 是否完成”，以 **代码存在 + 本地可复跑验证（API smoke + Playwright E2E）** 为准。

## 快速结论

- Sprint 1：已实现，并已通过 Upload/Version/WOPI/索引联动验证
- Sprint 2：已实现，并已通过 Search（含重试）+ UI 浏览/上传验证
- Sprint 3：已实现（规则引擎/ML/安全增强/分享/回收站/用户组管理），并已通过 smoke 与 E2E 验证
- Sprint 4：已实现（Folder tree/面包屑/文件夹管理/分面搜索端点），并已通过 UI 浏览验证；部分高级能力可按需补充专项用例

## 可复跑验证入口（建议作为“完成”判定依据）

### 1) API smoke（覆盖：Users/Groups + Workflow + Upload/Search/WOPI + Share/Tag/Category + Trash）

```bash
bash scripts/get-token.sh admin admin
ECM_UPLOAD_FILE="/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf" \
  ECM_API=http://localhost:7700 \
  ECM_TOKEN_FILE=tmp/admin.access_token \
  bash scripts/smoke.sh | tee tmp/smoke.latest.log
```

- 脚本：`scripts/smoke.sh`
- token：`scripts/get-token.sh`（输出：`tmp/admin.access_token`）

### 2) 前端 Playwright E2E（覆盖：New Folder/Upload/Edit Online/Search/Tags/Categories/Share/Permissions/Versions/ML/Workflow/Trash/Rules/Admin）

```bash
cd ecm-frontend
npm run e2e
```

- 测试：`ecm-frontend/e2e/ui-smoke.spec.ts`

## Sprint 1：Document Processing Pipeline（文档处理管道）

**规划文档**
- `docs/SPRINT_1_DOCUMENT_PIPELINE.md`

**代码落点（关键）**
- Pipeline：`ecm-core/src/main/java/com/ecm/core/pipeline/DocumentProcessingPipeline.java`
- 处理器：`ecm-core/src/main/java/com/ecm/core/pipeline/processor/`（存储/抽取/持久化/索引）
- 上传入口：`ecm-core/src/main/java/com/ecm/core/controller/UploadController.java`

**验证覆盖**
- Upload：`POST /api/v1/documents/upload`
- Versions：`GET /api/v1/documents/{id}/versions`
- WOPI：`GET /api/v1/integration/wopi/url/{id}` + Host 端到端（LOCK/PutFile/UNLOCK 后版本递增）
- Search：上传后可被检索（脚本内含重试）

## Sprint 2：Search + Web UI（全文搜索 + Web 界面）

**规划文档**
- `docs/SPRINT_2_SEARCH_AND_WEB_UI.md`

**代码落点（关键）**
- 搜索服务：`ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- 搜索端点：`ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- 前端浏览/上传/搜索：`ecm-frontend/src/pages/FileBrowser.tsx`、`ecm-frontend/src/components/dialogs/UploadDialog.tsx`、`ecm-frontend/src/pages/SearchResults.tsx`

**验证覆盖**
- API：`GET /api/v1/search`、`POST /api/v1/search/advanced`
- UI：Browse → Upload → Search Results（Playwright 有重试，避免索引最终一致性抖动）

## Sprint 3：Rule Engine + Security Enhancement（规则引擎 + 安全增强）

**规划文档**
- `docs/SPRINT_3_RULE_ENGINE.md`
- `docs/SPRINT_3_SECURITY_ENHANCEMENT.md`

**代码落点（关键）**
- Rules：`ecm-core/src/main/java/com/ecm/core/controller/RuleController.java`
- Rule engine：`ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java`
- ML：`ecm-core/src/main/java/com/ecm/core/controller/MLController.java`、`ml-service/app/main.py`
- Share links：`ecm-core/src/main/java/com/ecm/core/controller/ShareLinkController.java`
- Trash：`ecm-core/src/main/java/com/ecm/core/controller/TrashController.java`
- 用户/组（Keycloak 管理后端）：`ecm-core/src/main/java/com/ecm/core/controller/UserController.java`、`ecm-core/src/main/java/com/ecm/core/controller/GroupController.java`
- Workflow（Flowable）：`ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`、`ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`、`ecm-core/src/main/resources/workflows/document-approval.bpmn20.xml`

**关键校验点 / 已修复点**
- Workflow 定义自动部署：新增 `ecm-core/src/main/java/com/ecm/core/config/WorkflowDeploymentRunner.java`，避免 “No process definition found”。
- UI 删除进入回收站：修复软删除未写入 `deleted_at/deleted_by` 导致回收站排序/恢复不稳定（`ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java`、`ecm-core/src/main/java/com/ecm/core/service/NodeService.java`、`ecm-core/src/main/java/com/ecm/core/service/FolderService.java`）。

**验证覆盖**
- Rules 列表：`GET /api/v1/rules`
- ML health：`GET /api/v1/ml/health`
- Share：`POST /api/share/nodes/{id}`
- Trash：Move/Restore
- Admin：Users/Groups 列表与组成员增删（smoke 覆盖）
- Workflow：启动审批 → 获取任务 → 完成任务 → history 记录（smoke + UI E2E 覆盖）

## Sprint 4：UX Enhancement（文件夹与分面搜索体验）

**规划文档**
- `docs/SPRINT_4_UX_ENHANCEMENT.md`

**代码落点（关键）**
- Folder tree：`ecm-frontend/src/components/browser/FolderTree.tsx`
- Breadcrumb：`ecm-frontend/src/components/browser/FileBreadcrumb.tsx`
- Folder API：`ecm-core/src/main/java/com/ecm/core/controller/FolderController.java`
- Faceted search：`ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`

**验证覆盖**
- UI：目录树导航 + 面包屑（Playwright 校验 Root 不重复）
- Copy/Move：API 执行复制/移动并在 UI 列表验证（Playwright 覆盖）
- Facets：`/api/v1/search/facets` 返回 tags/categories facets（smoke + Playwright 覆盖）
- 侧边栏支持拖拽调宽、双击复位；Settings 提供紧凑模式（额外增强）：`ecm-frontend/src/pages/SettingsPage.tsx`

## 备注：判定边界

- “完成”判定以 **核心能力可运行 + 能被自动化 smoke/E2E 复跑** 为准。
- Sprint 文档内提到但未纳入自动化用例的能力（如更复杂的分面/批处理等），可在后续按优先级补充专项测试用例与验收点。
