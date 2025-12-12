# Sprint 3 完成报告：智能自动化与规则引擎

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

本次迭代（Sprint 3）重点实现了 Athena ECM 的**智能自动化**能力。通过引入独立的 Python ML 微服务和强大的规则引擎，系统现在能够自动理解文档内容、自动分类、并根据用户定义的规则执行自动化操作。同时，我们完善了事件驱动架构，确保 ECM 能与 DedupCAD 和 PLM 等外部系统无缝集成。

## 2. 架构变更

引入了 "Sidecar" 模式的 ML 微服务，将 AI/ML 负载从 Java 主进程中分离。

```mermaid
graph TD
    Client[前端/API] --> Core[ECM Core (Java)]
    Core --> DB[(PostgreSQL)]
    Core --> MQ[(RabbitMQ)]
    
    subgraph "Document Pipeline"
        P1[Tika Extraction]
        P2[Persistence]
        P3[ML Classification]
        P4[Rule Engine]
        P5[Event Publishing]
    end
    
    Core -- Pipeline --> P1
    P3 -- HTTP --> ML[ML Service (Python)]
    ML --> Model[(Model File)]
    
    P5 --> MQ
```

## 3. 功能实现详情

### 3.1 ML 微服务 (Machine Learning Service)

我们选择 **FastAPI + scikit-learn** 构建轻量级 ML 服务，避免了在 JVM 中运行 Python 的复杂性。

*   **技术栈**: Python 3.11, FastAPI, scikit-learn, numpy.
*   **核心功能**:
    *   `POST /api/ml/classify`: 基于 TF-IDF + MLP (多层感知机) 的文本分类。
    *   `POST /api/ml/train`: 支持通过 API 触发模型在线训练。
    *   `POST /api/ml/suggest-tags`: 关键词提取（基于 TF-IDF 权重）。
*   **部署**: 独立 Docker 容器 `ml-service`，通过 HTTP 与 Core 通信。
*   **容错**: Java 客户端 (`MLServiceClient`) 实现了熔断与降级，ML 服务不可用时 pipeline 仅跳过分类步骤，不影响文档上传。

### 3.2 规则引擎 (Rule Engine)

实现了基于 JSONB 存储的动态规则引擎，支持复杂的条件组合。

*   **数据模型**:
    *   **Rule**: 包含触发器类型 (`DOCUMENT_CREATED` 等)、优先级、启用状态。
    *   **Condition**: 支持嵌套 (AND/OR/NOT) 和多种运算符 (Equals, Contains, Regex, GT/LT 等)。
    *   **Action**: 支持多种执行动作 (Add Tag, Move, Set Category, Webhook 等)。
*   **服务层**: `RuleEngineService` 负责解析 JSON 条件树并在内存中高效评估。
*   **API**: 提供了完整的 CRUD 和 `POST /test` 接口，允许在不保存规则的情况下测试匹配逻辑。

### 3.3 文档处理管道集成

管道 (`DocumentProcessingPipeline`) 扩展了两个关键处理器：

1.  **MLClassificationProcessor (Order: 400)**:
    *   在文本提取和持久化之后执行。
    *   调用 ML 服务获取建议分类。
    *   若置信度 > 0.85 (配置项 `auto-apply-threshold`)，自动应用分类。

2.  **EventPublishingProcessor (Order: 600)**:
    *   在管道末端执行。
    *   发送标准化事件 `DocumentCreatedMessage` 到 RabbitMQ。
    *   **集成价值**: 下游系统 (DedupCAD) 可监听此消息进行查重，PLM 系统可监听此消息触发审批流。

### 3.4 辅助功能

*   **垃圾箱 (Trash)**: 实现了软删除、恢复、永久删除和自动清理逻辑。
*   **分享链接 (Share Link)**: 实现了带密码、有效期和访问次数限制的外部文件分享功能。

## 4. API 接口清单

### 规则管理
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/v1/rules` | 获取规则列表 |
| POST | `/api/v1/rules` | 创建规则 |
| POST | `/api/v1/rules/{id}/test` | 测试规则匹配 |
| GET | `/api/v1/rules/templates` | 获取预置模板 |

### ML 服务
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/ml/classify` | 手动触发文本分类 |
| POST | `/api/v1/ml/train` | 提交数据训练模型 |

### 垃圾箱与分享
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/trash` | 查看回收站 |
| POST | `/api/trash/{id}/restore` | 恢复文件 |
| POST | `/api/share/nodes/{id}` | 创建分享链接 |

## 5. 配置与部署

### 环境变量 (.env)
新增了以下配置项：
```ini
# ML Service
ML_SERVICE_URL=http://ml-service:8080
ECM_ML_ENABLED=true

# MinIO (Object Storage)
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
```

### Docker Compose
*   新增 `ml-service` 服务。
*   新增 `minio` 服务。
*   `ecm-core` 增加了对上述服务的依赖和网络配置。

## 6. 验证方法

运行冒烟测试脚本可验证端到端流程：

```bash
# 确保服务已启动
docker-compose up -d

# 获取 Token (如果启用了 Auth)
export ECM_TOKEN=... 

# 运行冒烟测试
./scripts/smoke.sh
```

**预期结果**:
1.  Health Check 通过。
2.  Pipeline 状态正常。
3.  文档上传成功 (自动触发 ML 分类和规则)。
4.  分享链接创建成功。
5.  文档移入回收站并恢复成功。

## 7. 后续计划 (Next Steps)

*   **前端适配**: 为规则引擎开发可视化的 "查询构建器" (Query Builder) UI。
*   **模型优化**: 收集更多真实文档样本，定期重新训练 ML 模型以提高准确率。
*   **更多动作**: 增加 PDF 水印、OCR 语言选择等规则动作。
