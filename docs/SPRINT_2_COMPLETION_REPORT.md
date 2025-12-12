# Sprint 2 完成报告：工作流引擎集成 (Workflow Engine)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

本次迭代（Sprint 2）实现了核心的**工作流引擎集成**。基于 Flowable 引擎，我们构建了文档审批流程，支持文档状态的自动流转和用户任务管理。

## 2. 核心功能实现

### 2.1 审批流程定义 (Document Approval Process)

定义了标准的 BPMN 2.0 流程 `documentApproval`，流程逻辑如下：
1.  **启动**: 设置文档状态为 `PENDING_APPROVAL`。
2.  **审批任务**: 分配给指定审批人。
3.  **决策网关**: 根据审批结果（批准/拒绝）分支。
4.  **状态更新**:
    *   批准 -> `APPROVED`
    *   拒绝 -> `REJECTED`

文件位置: `src/main/resources/workflows/document-approval.bpmn20.xml`

### 2.2 Workflow Service

封装了 Flowable 的核心能力，提供业务友好的接口：
*   `startDocumentApproval`: 启动流程，传递业务变量 (documentId, approvers)。
*   `completeTask`: 完成用户任务。
*   `updateDocumentStatus`: 被 BPMN 服务任务调用的 Java 方法，用于更新 ECM 文档状态并记录审计日志。

### 2.3 Workflow API

重构后的 REST API (`/api/v1/workflows`) 提供了以下能力：
*   `GET /definitions`: 查看可用流程。
*   `POST /document/{id}/approval`: 发起文档审批。
*   `GET /tasks/my`: 查看我的待办任务。
*   `POST /tasks/{id}/complete`: 审批/拒绝任务。
*   `GET /document/{id}/history`: 查看审批历史。

## 3. 架构变更

*   **NodeStatus**: 新增 `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `DRAFT` 状态。
*   **Security**: 审批操作集成了现有的 `SecurityService` (当前用户上下文)。
*   **Audit**: 工作流事件（启动、状态变更）自动写入 `AuditLog`。

## 4. 验证方法

验证工作流功能：

```bash
# 1. 启动服务
docker-compose up -d

# 2. 发起审批 (假设 documentId 为有效 UUID)
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"approvers": ["admin"], "comment": "Please review"}' \
  http://localhost:8080/api/v1/workflows/document/{documentId}/approval

# 3. 查看任务
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/workflows/tasks/my

# 4. 完成任务 (批准)
# 获取 taskId 后:
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"approved": true}' \
  http://localhost:8080/api/v1/workflows/tasks/{taskId}/complete
```

## 5. 后续计划

*   **UI 集成**: 在前端文件浏览页面添加 "发起审批" 按钮和 "我的任务" 列表。
*   **复杂流程**: 支持多级审批、并行审批。
*   **通知**: 集成邮件通知（Sprint 6）。
