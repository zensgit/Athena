# 功能开发报告：智能元数据与自动匹配 (Sprint 12)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

Sprint 12 旨在解决“文档归档繁琐”的痛点。通过引入 **对应人 (Correspondent)** 概念和 **自动匹配算法**，系统现在可以根据文档内容（OCR 文本）自动识别文件来源（例如自动将含有“中国电信”字样的 PDF 归类为“中国电信”发来的账单）。

## 2. 核心功能实现

### 2.1 对应人管理 (`Correspondent`)
*   **概念**: 对应人是文档的“发件方”或“关联方”。区别于标签，它是文档的一个核心属性（字段）。
*   **模型**: 包含名称、匹配算法 (`matchAlgorithm`)、匹配模式 (`matchPattern`) 等字段。
*   **API**: 提供了标准的 CRUD 接口 `/api/v1/correspondents`。

### 2.2 自动匹配算法 (`CorrespondentService`)
移植了 Paperless-ngx 的匹配逻辑，支持以下算法：
*   **ANY**: 文本中包含模式中的任意一个词。
*   **ALL**: 文本中包含模式中的所有词。
*   **EXACT**: 文本完全等于模式。
*   **REGEX**: 正则表达式匹配（最强大）。

### 2.3 管道集成 (`AutoMatchingProcessor`)
*   **位置**: Order 350（在文本提取之后，索引之前）。
*   **逻辑**: 读取 `extractedText` -> 遍历所有定义了规则的对应人 -> 运行匹配算法 -> 命中则自动关联 -> 更新数据库。

## 3. 验证方法

### 3.1 配置规则
```bash
# 创建一个对应人 "AWS"，匹配规则为 "Amazon Web Services" (不区分大小写)
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "AWS",
    "matchAlgorithm": "ANY",
    "matchPattern": "Amazon AWS",
    "insensitive": true
  }' \
  http://localhost:8080/api/v1/correspondents
```

### 3.2 上传测试
上传一个包含 "Amazon Web Services Inc." 字样的 PDF 发票。

### 3.3 验证结果
查看文档详情，其 `correspondent` 字段应自动关联为 "AWS"。

## 4. 后续计划

*   **文档类型匹配**: 将同样的逻辑扩展到 `ContentType`（文档类型）的自动推断。
*   **前端展示**: 在文件列表和详情页显示“对应人”信息，并支持按对应人筛选。
