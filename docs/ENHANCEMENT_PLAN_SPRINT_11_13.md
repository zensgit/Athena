# Athena ECM - 功能移植与增强计划 (Sprint 11-13)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: 规划中
> **参考项目**: Paperless-ngx, Alfresco

## 1. 概述

本项目计划旨在通过借鉴开源社区的最佳实践，将 Athena ECM 从一个标准的内容管理系统升级为具备**主动摄取**、**智能分类**和**企业级健壮性**的综合平台。

---

## 📅 Sprint 11: 主动摄取与邮件自动化 (Mail Automation)

**核心参考**: `Paperless-ngx (paperless_mail)`
**目标**: 实现基于 IMAP 的邮件自动抓取与处理，打通“邮件 -> 归档”的自动化链路。

### 1.1 邮件账户管理 (Mail Accounts)
*   **功能**: 支持配置多个 IMAP 邮箱账户。
*   **数据模型**: `MailAccount`
    *   `host`, `port`, `username`, `password` (加密)
    *   `security`: NONE, SSL, STARTTLS
    *   `enabled`: 是否启用轮询
*   **技术栈**: Jakarta Mail (JavaMail)

### 1.2 邮件处理规则 (Mail Rules)
*   **功能**: 用户定义规则，决定哪些邮件需要处理以及如何处理。
*   **规则条件**:
    *   `subject_filter`: 主题匹配模式 (支持正则)
    *   `from_filter`: 发件人匹配
    *   `body_filter`: 正文匹配
*   **处理动作**:
    *   `action`: 仅附件 / 附件+正文 / 仅正文
    *   `assign_tag`: 自动打标
    *   `assign_type`: 自动指定文档类型
*   **后续动作**: 删除邮件 / 标记为已读 / 移动到文件夹

### 1.3 邮件抓取服务 (Mail Fetcher)
*   **服务**: `MailFetcherService`
*   **机制**: 基于 Spring `@Scheduled` 的后台任务。
*   **流程**:
    1.  连接 IMAP 服务器。
    2.  搜索符合条件的未读邮件。
    3.  下载附件和正文（解析为 PDF/HTML）。
    4.  调用 `DocumentUploadService` 摄取文档。
    5.  应用元数据（Tags, Types）。
    6.  执行邮件清理操作。

---

## 📅 Sprint 12: 智能元数据与自动匹配 (Intelligent Metadata)

**核心参考**: `Paperless-ngx (Correspondents, Document Types)`
**目标**: 引入“对应人”概念，并实现基于内容的元数据自动匹配算法。

### 2.1 对应人管理 (Correspondents)
*   **概念**: 文档的来源方（如供应商、银行、客户）。
*   **模型**: `Correspondent` (name, match_pattern, match_algorithm)。
*   **价值**: 提供除了 Tags 之外的另一个关键归档维度。

### 2.2 自动匹配算法 (Matching Algorithms)
*   **功能**: 在文档上传并 OCR 后，自动根据内容匹配元数据。
*   **支持算法**:
    *   **ANY**: 包含任意关键词。
    *   **ALL**: 包含所有关键词。
    *   **EXACT**: 内容完全匹配。
    *   **REGEX**: 正则表达式匹配。
    *   **FUZZY**: 模糊匹配 (Levenshtein Distance)。

### 2.3 管道集成
*   **处理器**: `AutoMatchingProcessor` (Order: 350)
*   **逻辑**: 在文本提取后执行，根据预设规则自动填充 `correspondent_id`、`document_type_id` 和 `tag_ids`。

---

## 📅 Sprint 13: 系统健壮性与高级转换 (Robustness & Transformation)

**核心参考**: `Paperless-ngx (Tasks)` & `Alfresco (Transformers)`
**目标**: 确保数据一致性，并增强文档预览能力。

### 3.1 一致性检查 (Sanity Checker)
*   **服务**: `SanityCheckService`
*   **检测项**:
    *   **Missing Files**: 数据库有记录，但存储桶中无文件。
    *   **Orphan Files**: 存储桶中有文件，但数据库无记录。
    *   **Checksum Mismatch**: 文件哈希值与数据库记录不符（数据损坏）。
*   **报告**: 生成详细的健康检查报告。

### 3.2 高级转换管道 (Advanced Transformers)
*   **参考**: Alfresco 的链式转换器。
*   **增强**:
    *   **ImageMagick 集成**: 处理高分辨率图片的缩放和格式转换。
    *   **HTML 转 PDF**: 优化邮件正文的归档效果。
    *   **多版本渲染 (Renditions)**: 自动生成缩略图、Web 预览图和打印版 PDF。

---

## 📈 实施建议

建议按照 **Sprint 11 -> 12 -> 13** 的顺序执行。
1.  **邮件自动化** 能带来最直接的用户价值，解决“文件源头”问题。
2.  **智能匹配** 建立在内容摄取之上，解决“整理”问题。
3.  **健壮性** 是系统长期运行的保障，解决“运维”问题。
