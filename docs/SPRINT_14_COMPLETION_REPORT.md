# 功能开发报告：高级组织 (Advanced Organization)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

Sprint 14 专注于提升文档的**逻辑组织能力**。我们借鉴了 Alfresco 和 Paperless 的高级特性，实现了**智能文件夹**（虚拟视图）、**文档关联**（语义连接）和 **ASN**（物理归档编号）。

## 2. 核心功能实现

### 2.1 智能文件夹 (Smart Folders)
*   **概念**: 智能文件夹不实际存储文件，而是存储一个“查询条件”。当用户打开该文件夹时，系统实时执行查询并展示结果。
*   **实现**:
    *   `Folder` 实体新增 `isSmart` 和 `queryCriteria` (JSON) 字段。
    *   `FolderService.getFolderContents` 被改造：如果检测到是智能文件夹，则调用 `FacetedSearchService` 执行动态查询，并将结果映射为节点列表返回。
    *   **价值**: 实现了 "My Invoices", "Recent Contracts" 等动态视图，无需人工整理。

### 2.2 文档关联 (Document Relations)
*   **概念**: 建立文档之间的双向链接。
*   **实体**: `DocumentRelation` (Source -> Target, Type)。
*   **类型**: 支持自定义类型，如 `RELATED` (相关), `APPENDIX` (附件), `REPLACES` (替代)。
*   **API**: 提供了创建、删除和查询关联的接口。

### 2.3 档案序列号 (ASN)
*   **概念**: Archive Serial Number，用于连接数字世界与物理纸质文件（例如贴在纸质合同上的编号）。
*   **实现**: `Document` 实体新增 `archiveSerialNumber` 字段，配合 `AsnGeneratorService` 自动生成递增编号。

## 3. API 接口

### 关联管理
*   `POST /api/v1/relations`: 创建关联。
*   `GET /api/v1/relations/{id}`: 获取某文档发出的关联。
*   `GET /api/v1/relations/{id}/incoming`: 获取指向某文档的关联。

### 智能文件夹
*   使用标准的创建文件夹接口 `POST /api/v1/folders`，但在 Body 中指定 `isSmart: true` 和 `queryCriteria`。

## 4. 验证方法

### 4.1 创建智能文件夹
```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "Smart: My PDFs",
    "isSmart": true,
    "queryCriteria": {
      "filters": { "mimeTypes": ["application/pdf"] }
    }
  }' \
  http://localhost:8080/api/v1/folders
```
然后访问该文件夹的内容接口，应返回所有 PDF 文件。

### 4.2 建立关联
```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sourceId": "doc-uuid-1", "targetId": "doc-uuid-2", "type": "RELATED"}' \
  http://localhost:8080/api/v1/relations
```
