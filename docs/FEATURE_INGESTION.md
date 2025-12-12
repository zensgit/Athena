# 功能开发报告：自动化摄取 (Ingestion Automation)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

Sprint 9 致力于提升 Athena ECM 的“无人值守”处理能力。通过监听本地目录和识别文档中的条码，系统现在可以作为扫描仪或传真机的后端，自动完成文档的归档和分类。

## 2. 核心功能

### 2.1 目录监控 (Directory Watcher)
一个后台服务 (`DirectoryWatcherService`)，模仿 Paperless-ngx 的 "Consumption Directory" 功能。
*   **机制**: 每 10 秒轮询 `/var/ecm/import` 目录。
*   **流程**: 发现文件 -> 调用 Upload Service (走完整 Pipeline) -> 删除源文件 (或移至 Error 目录)。
*   **配置**:
    ```yaml
    ecm.ingestion.enabled=true
    ecm.ingestion.watch-folder=/var/ecm/import
    ```

### 2.2 条码识别 (Barcode Processor)
集成到文档处理管道中的新处理器 (`BarcodeProcessor`, Order 150)。
*   **能力**: 使用 Google ZXing 库扫描 PDF (第一页) 或图片文件。
*   **输出**: 将识别到的条码/二维码内容存入 `metadata.barcodes`。
*   **智能标签**: 如果条码内容以 `TAG:` 开头 (例如 `TAG:INVOICE`)，系统会自动将 `INVOICE` 添加为文档标签。这提供了一种极其廉价的自动化分类方案：只需在扫描前贴上对应的二维码贴纸。

## 3. 验证方法

### 3.1 测试目录监控
```bash
# 1. 创建监控目录
mkdir -p /tmp/ecm-import

# 2. 启动应用 (需配置 ecm.ingestion.watch-folder=/tmp/ecm-import)

# 3. 放入文件
cp test.pdf /tmp/ecm-import/

# 4. 观察日志，应显示 "Detected new file" -> "Successfully ingested"
# 5. 检查 /tmp/ecm-import/test.pdf 是否被删除
```

### 3.2 测试条码
上传一个包含二维码的图片。检查返回的 Document 对象，`metadata` 中应包含 `barcodes` 字段。

## 4. 后续扩展

*   **文档拆分**: 基于特定的 "PATCH-T" 分隔页条码自动拆分 PDF。
*   **ASN 码**: 使用条码内容作为 Archive Serial Number (ASN)。
