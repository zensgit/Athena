# 功能开发报告：系统健壮性检查 (Sanity Check)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

为了确保长期运行中数据的完整性和一致性，我们借鉴 Paperless-ngx 的设计，实现了 **Sanity Check (健全性检查)** 机制。该机制可以定期扫描数据库和文件存储，发现并报告不一致的情况（如文件丢失、元数据损坏）。

## 2. 核心功能实现

### 2.1 检查器框架 (`SanityChecker`)
*   定义了标准的检查接口 `check(boolean fix)`，支持只读检测和尝试修复两种模式。
*   返回标准化的 `SanityCheckReport`，包含检查项、状态、问题列表和修复记录。

### 2.2 核心检查项
*   **Missing File Checker (`MissingFileChecker`)**:
    *   遍历数据库中所有 `Document` 记录。
    *   检查对应的 `contentId` 在存储层（MinIO/FileSystem）中是否真实存在。
    *   **价值**: 及时发现因存储故障或误删导致的“死链”文档。

### 2.3 调度与执行 (`SanityCheckService`)
*   **统一入口**: `runAllChecks` 依次执行所有注册的检查器。
*   **定时任务**: 默认每周日凌晨 3 点自动执行一次全量检查（只读模式），并在日志中输出报告。

## 3. API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/system/sanity/run?fix=false` | 手动触发检查。仅限管理员。 |

## 4. 验证方法

```bash
# 触发检查
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/system/sanity/run

# 预期输出:
# [
#   {
#     "checkName": "Missing File Checker",
#     "status": "SUCCESS",
#     "itemsChecked": 1500,
#     "issuesFound": 0
#   }
# ]
```

## 5. 后续计划

*   **Orphan File Checker**: 反向检查存储桶中存在但数据库无记录的孤儿文件。
*   **Checksum Verification**: 定期重新计算文件哈希值并与数据库记录比对，防止静默数据腐烂 (Bit Rot)。
