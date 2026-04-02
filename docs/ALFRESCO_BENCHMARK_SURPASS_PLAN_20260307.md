# Alfresco 对标与超越计划（2026-03-07）

## 参考代码库
- `/Users/huazhou/Downloads/Github/Athena/reference-projects/alfresco-community-repo`

## 对标结论（核心）

### 1) 搜索能力
- Alfresco 关键实现：
  - `remote-api/.../search/SearchApiWebscript.java`
  - `remote-api/.../search/model/SearchQuery.java`
  - `remote-api/.../search/impl/SearchMapper.java`
  - `remote-api/.../search/impl/ResultMapper.java`
- 结论：
  - Athena 已具备实用高级搜索（facet/highlight/spellcheck/suggestions/previewStatus）；
  - 主要差距是统一 DSL 可扩展性（range/pivot/stats/scope/context echo）。

### 2) 预览与 rendition
- Alfresco 关键实现：
  - `remote-api/.../impl/RenditionsImpl.java`
  - `repository/.../rendition2/RenditionService2Impl.java`
  - `repository/.../thumbnail/ThumbnailServiceImpl.java`
  - `repository/.../transform/LocalFailoverTransform.java`
- 结论：
  - Athena 在失败治理上已有优势（dead-letter、自动重放、失败分类、CAD failover）；
  - 差距在“rendition 作为一等资源”的状态模型与适配能力暴露。

### 3) 批处理与导出
- Alfresco 关键实现：
  - `repository/.../batch/BatchProcessor.java`
  - `remote-api/.../impl/DownloadsImpl.java`
  - `remote-api/.../bulkimport/AbstractBulkFileSystemImportWebScript.java`
  - `repository/.../exporter/ExporterComponent.java`
- 结论：
  - Athena 的同步批量/下载能力存在差距；
  - 优先补齐任务化导出、可轮询状态、可取消。

## 超越路线（按优先级）
1. 导出任务中心（create/status/cancel/download），先覆盖预览批处理导出，再扩展到批量下载/审计导出。
2. 通用批处理框架（分批、并发 worker、失败重试、统计）。
3. rendition 资源模型（CREATED/NOT_CREATED/STALE + fail reason）。
4. 搜索 DSL 扩展（range/pivot/stats/scope/context echo）。

## 本轮落地（对标路线中的第一步）
- 已在 Athena 实现预览 dry-run CSV 导出能力（Phase 200）：
  - 后端导出接口（admin）；
  - 前端高级搜索一键导出；
  - E2E 与后端安全测试覆盖。
- 这为后续“异步导出任务中心”提供了稳定的导出契约和前端交互入口。
