# 功能开发报告：PDF 高级处理 (Merge/Split)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

为了增强文档处理能力，参考 Paperless-ngx 的功能集，我们在系统中集成了 **PDF 高级处理** 模块。这允许用户直接在服务器端执行 PDF 的合并与拆分操作，无需下载到本地处理后再上传。

## 2. 核心功能实现

### 2.1 技术栈
*   **Apache PDFBox**: Java 生态中最成熟的开源 PDF 处理库。

### 2.2 服务层 (`PdfManipulationService`)

*   **合并 (Merge)**:
    *   `mergePdfs(List<UUID> ids, String name)`
    *   接收多个文档 ID，验证其 MIME 类型是否为 PDF。
    *   按顺序将页面合并到一个新的 `PDDocument`。
    *   自动将结果保存为新文档，并存储到指定目录。
    *   **应用场景**: 将多份扫描的合同页合并为一个文件；将发票与附件合并。

*   **拆分 (Split)**:
    *   `splitPdf(UUID id)`
    *   将一个多页 PDF 拆分为多个单页 PDF。
    *   自动命名: `原文件名_page_1.pdf`, `原文件名_page_2.pdf`...
    *   **应用场景**: 从扫描的大文件中提取特定单据。

### 2.3 API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/tools/pdf/merge` | 合并多个 PDF |
| POST | `/api/v1/tools/pdf/{id}/split` | 拆分 PDF |

## 3. 验证方法

```bash
# 1. 准备两个 PDF 文档 ID (doc1, doc2)

# 2. 合并
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "documentIds": ["uuid-1", "uuid-2"],
    "newName": "Merged-Contract.pdf",
    "targetFolderId": "folder-uuid"
  }' \
  http://localhost:8080/api/v1/tools/pdf/merge

# 3. 拆分
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/tools/pdf/{uuid-1}/split
```

## 4. 价值

这一功能填补了 `ECM_FEATURE_COMPARISON.md` 中 **P2 级 (文档处理)** 的空白，使得 Athena ECM 在文档处理能力上与专业竞品（如 Paperless-ngx）看齐。