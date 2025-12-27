# PDF 批量上传与预览验证报告 (2025-12-25)

## 环境
- Backend: http://localhost:7700
- Frontend: http://localhost:5500 (本次使用 API 验证)
- Keycloak: http://localhost:8180
- 账号: admin

## 操作步骤
1. 使用 `scripts/get-token.sh` 获取 admin token。
2. 在 `uploads` 根目录创建批量测试文件夹：`pdf-batch-20251225_144551`。
3. 依次上传 6 个 PDF。
4. 调用 `/api/v1/documents/{id}/preview` 校验预览可用性与页数。

## 结果
| 文件名 | Document ID | 预览支持 | 页数 | 备注 |
| --- | --- | --- | --- | --- |
| 改图28.pdf | 8e3e86e6-c3c4-439d-b113-59bb140247ce | true | 1 | HTTP 200 |
| 简易三坐标数控机床设计.pdf | bd9d78f6-9b7b-4b9f-85e0-0ae0d527edd0 | true | 53 | HTTP 200 |
| 3-291-249-885-00_RevB1_mx.pdf | 42715e9d-440f-4dd2-b232-06d503b4669f | true | 1 | HTTP 200 |
| 3-752-062-962-00_RevB2_mx.pdf | 1d022505-60e8-4003-8336-a60e5177a323 | true | 1 | HTTP 200 |
| 3-752-063-959-00_RevC1_mx.pdf | 192cccf8-88a9-4832-9dea-f87eb3df9044 | true | 1 | HTTP 200 |
| 1-490-031-395-00_RevD.PDF | 3b7d2ada-37f1-45cf-904c-1b1f26d942d3 | true | 1 | HTTP 200 |

## 产物
- 批量目录 ID: `7979477f-429e-4d4f-b47f-3c6f6f701b13`
- 预览验证日志: `tmp/pdf-batch-20251225_144551-preview.tsv`

## 结论
- 6 个 PDF 均上传成功。
- 预览接口返回 `supported=true` 且 HTTP 200。
- 预览页数与文件预期一致（其中“简易三坐标数控机床设计.pdf”为 53 页）。
