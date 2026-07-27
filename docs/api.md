# API 文档（bid-winning-backend）

> 本文档描述 bid-winning-backend 提供的全部对外 HTTP 接口。共 30 端点（含 2 个资信文件端点、1 个 AI 风险分析触发端点 + 2 个风险记录只读查询端点）。
>
> 内部架构：[`docs/architecture.md`](architecture.md) · 数据库结构：[`docs/database.md`](database.md)

---

## 0. 文档元信息

**Base URL**：`http://{host}:{port}`

- dev 默认：`http://localhost:8080`
- prod：`http://{prod-host}:80`

**请求体**：业务 JSON 端点使用 `application/json; charset=utf-8`；文件上传端点使用 `multipart/form-data`。

**字符编码**：UTF-8

### 端点速览

| 模块 | 端点数 | 路径前缀 |
|------|:---:|---------|
| 服务状态 + 邮件测试 | 3 | `/service-status`、`/send-email`、`/send-notification` |
| 招标经办人 (BidHandlerInfo) | 7 | `/bid-handler-info/...` |
| 招标公告 (InvitationForBid) | 8 | `/invitation-for-bid/...` |
| 资信文件 (CredentialFile) | 2 | `/credential-files/...` |
| 投标记录 (BidRecord) | 7 | `/bid-record/...` |
| **AI 风险分析**（v0.1+） | 1 | `/bid-record/analyze-risk/{id}` |
| **风险记录查询**（v0.2+，敏感数据只读） | 2 | `/risk-analysis/...` |

> **v18 命名变更**：招标方相关表统一使用 World Bank 术语"Invitation for Bids"；经办人使用"bid handler"。详见 [database.md §1](database.md#1-表结构总览)。

**AI 风险分析端点**（详细见 Controller 源码）：

| Method | Path | 说明 |
|--------|------|------|
| POST | `/bid-record/analyze-risk/{id}` | 校验资信批次和文件均已转换，汇总 Markdown 后同步调 OpenAI 兼容 LM Studio，解析 JSON，落库，返全字段 `RiskAnalysis` |

---

## 1. 通用约定

### 1.1 统一响应结构（Result）

所有业务端点统一返回 `Result<T>`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": <T>
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务状态码（见 §1.2） |
| `message` | String | 文字消息 |
| `data` | T | 业务数据，无数据时为 `null` |

`data` 类型与端点对应：

| 端点类型 | data 类型 |
|---------|----------|
| 创建 / 查询 | `Entity` |
| 部分更新（PATCH） | `Diff<Entity>` |
| 删除 | `null` |
| 列表查询 | `PageInfo<Entity>` |
| 按招标公告查询投标记录 | `List<Entity>`（不分页） |
| 批量删除 | `List<Entity>`（实际删除的实体列表） |

**邮件端点**（`/send-email`、`/send-notification`）**不**返回 `Result<T>`，直接返回字符串。

### 1.2 业务状态码

| code | 含义 | 触发场景 |
|------|------|----------|
| 200 | 成功 | 业务正常完成 |
| 400 | 参数错误 | 分页参数错误 / `@Valid` 校验失败 |
| 404 | 资源不存在 | 按主键查询/更新/删除时记录不存在 |
| 500 | 服务器内部错误 | 异常未捕获（`GlobalExceptionHandler` 统一处理） |

### 1.3 PATCH 语义

`PATCH /{resource}/{id}` 遵循 RFC 5789：
- 传哪些字段就更新哪些字段
- 未传的字段（`null`）在 DB 中保持原值
- 由 XML 动态 SQL（`<set> + <if test="xxx != null">`）实现
- **不**支持"将某字段置空"

**Diff 结构**：
```json
{
  "before": { /* 字段更新前的值 */ },
  "after":  { /* 字段更新后的值 */ }
}
```

### 1.4 批量删除

`POST /{resource}/batch-delete`：
- 入参：`{ "ids": [1, 2, 3] }`（1-500 条，`@Size(max=500)` 校验）
- **永远 200**，`data` = 实际删除的实体列表（按 DB 物理顺序）
- 不存在的 ID 不在 `data` 中，前端 diff 自己请求的 ids 即可知道 missing
- 单次最多 500 条
- **不**做事务封装 / 不做级联 / 不保证外键引用完整性

### 1.5 分页约定

`GET /{resource}`：
- Query 参数：`page`（默认 1）、`pageSize`（默认 10）、`sort`（默认见各端点）
- `sort` 格式：PageHelper 原生（空格分隔），例 `"invitation_id desc"`
- 参数错误返 `code=400, message="分页查询参数错误"`

**PageInfo 结构**（`data` 字段）：
```json
{
  "total": 100,
  "list": [ /* 当前页数据 */ ],
  "pageNum": 1,
  "pageSize": 10,
  "pages": 10,
  "hasNextPage": true,
  "hasPreviousPage": false
}
```

### 1.6 时间 / 金额格式

| 字段类型 | JSON 格式 | 示例 |
|---------|-----------|------|
| 时间（DATETIME） | ISO-8601 字符串 | `"2026-06-15T14:30:00"` |
| 金额（DECIMAL） | JSON 数字（BigDecimal 序列化） | `67500.00` |
| 布尔（TINYINT(1)） | JSON 布尔 | `true` / `false` |

### 1.7 字段类型映射

| Java 类型 | JSON 类型 | 适用字段 |
|----------|----------|----------|
| String | string | 姓名、编号、文本 |
| Integer | number | 主键 ID、外键 ID |
| BigDecimal | number | 金额、成本、保证金 |
| Boolean | boolean | 是否固定价、是否需要征信 |
| LocalDateTime | string (ISO-8601) | 截止时间、创建时间 |
| List | array | 中标结果列表、批量删除 ID 列表 |

### 1.8 业务操作后邮件通知

> 历史方案（AOP 切面）已废弃。当前由 Service 层显式调用 `BaseService.sendEmail`。

| 端点 | 触发 | 收件人来源 |
|------|------|----------|
| `POST /bid-handler-info/add` | `service.sendOperationEmail(entity)` | `entity.targetEmail`（已落库） |
| `POST /bid-handler-info/batch-add` | `service.sendBatchAddEmail(created, request.targetEmail)` | `request.targetEmail`（批量汇总邮件，不落库到单一实体） |
| 其他 26 个端点 | — | 未发送 |

`targetEmail` 为 null/空时跳过发邮件。详见 [architecture.md §8](architecture.md#8-邮件通知service-层-baseservicesendemail)。

---

## 2. 服务状态 + 邮件测试（StatusCheckController）

### 2.1 `GET /service-status`

Spring 服务存活探活，返回固定字符串。

```
Service is up and running.
```

### 2.2 `POST /send-email`

发送纯文本邮件（同步）。

**请求体**（`PlainTextMailRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `to` | String | ✅ | 收件人邮箱 |
| `subject` | String | ✅ | 邮件主题 |
| `content` | String | ✅ | 邮件正文（纯文本） |
| `from` | String | ❌ | **实际无效**，发件人固定从配置 `spring.mail.username` 读取 |

**响应**：`Email sent successfully.`

```bash
curl -X POST http://{host}:{port}/send-email \
  -H "Content-Type: application/json" \
  -d '{"to":"user@example.com","subject":"测试","content":"Hello"}'
```

### 2.3 `POST /send-notification`

发送中标通知书 HTML 邮件（异步，延迟 5 秒）。

**请求体**（`BidWinningMailRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `to` | String | ✅ | 收件人（继承自 `PlainTextMailRequest`） |
| `subject` | String | ✅ | 邮件主题（继承） |
| `projectCode` | String | ✅ | 项目编号（模板变量） |
| `handlerName` | String | ✅ | 经办人姓名（模板变量） |
| `bidWinningList` | Array | ✅ | 中标结果列表（模板 `th:each` 循环） |
| `bidWinningList[].bidderName` | String | ✅ | 投标人名称（v18 重命名自 `tendererName`） |
| `bidWinningList[].bidWinningAmount` | String | ✅ | 中标价 |
| `templateName` | String | ✅ | 模板文件名（不含 `.html`） |

**响应**：`发送成功`（主线程立即返回，邮件后台异步发送）。

> 模板文件必须存在于 `src/main/resources/templates/{templateName}.html`，否则抛 `IllegalArgumentException`。

```bash
curl -X POST http://{host}:{port}/send-notification \
  -H "Content-Type: application/json" \
  -d '{
    "to": "user@example.com",
    "subject": "中标通知书",
    "projectCode": "PROJ001",
    "handlerName": "张三",
    "templateName": "bid-winning-notification",
    "bidWinningList": [
      { "bidderName": "A投标人", "bidWinningAmount": "67500.00" }
    ]
  }'
```

---

## 3. 招标经办人（BidHandlerInfo）

对应表 `bid_handler_info`，主键 `bid_handler_id`。

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| POST | `/bid-handler-info/add` | 创建 | `Result<BidHandlerInfo>` |
| POST | `/bid-handler-info/batch-add` | 批量新增 | `Result<List<BidHandlerInfo>>` |
| GET | `/bid-handler-info/{id}` | 按主键查询 | `Result<BidHandlerInfo>` |
| PATCH | `/bid-handler-info/{id}` | 按主键部分更新 | `Result<Diff<BidHandlerInfo>>` |
| DELETE | `/bid-handler-info/{id}` | 按主键删除 | `Result<Void>` |
| GET | `/bid-handler-info` | 列表查询（分页） | `Result<PageInfo<BidHandlerInfo>>` |
| POST | `/bid-handler-info/batch-delete` | 批量删除 | `Result<List<BidHandlerInfo>>` |

### 3.1 `POST /bid-handler-info/add`

**请求体**（`AddRequest`，3 字段必填 + 通知邮件收件人）：

| 字段 | 类型 | 校验 | 说明 |
|------|------|------|------|
| `handlerName` | String | @NotBlank, @Size(max=50) | 经办人姓名 |
| `companyName` | String | @NotBlank, @Size(max=100) | 所属采购公司 |
| `departmentName` | String | @NotBlank, @Size(max=100) | 所属部门 |
| `targetEmail` | String | @NotBlank, @Size(max=255) | 通知邮件收件人（已落库） |

```bash
curl -X POST http://{host}:{port}/bid-handler-info/add \
  -H "Content-Type: application/json" \
  -d '{"handlerName":"张三","companyName":"测试公司","departmentName":"采购部","targetEmail":"zhang@example.com"}'
```

### 3.2 `GET /bid-handler-info/{id}`

| Path 参数 | 类型 | 必填 |
|----------|------|:---:|
| `id` | Integer | ✅ |

返回 200 + 实体 或 404 + null。

### 3.3 `PATCH /bid-handler-info/{id}`

**请求体**（`UpdateRequest`，PATCH 风格，全部可选）：

| 字段 | 类型 | 校验 | 说明 |
|------|------|------|------|
| `handlerName` | String | @Size(max=50) | null = 不修改 |
| `companyName` | String | @Size(max=100) | 同上 |
| `departmentName` | String | @Size(max=100) | 同上 |
| `targetEmail` | String | — | 同上 |

```bash
curl -X PATCH http://{host}:{port}/bid-handler-info/1 \
  -H "Content-Type: application/json" \
  -d '{"handlerName":"李四"}'
```

### 3.4 `DELETE /bid-handler-info/{id}`

返回 200 + null 或 404 + null。

### 3.5 `GET /bid-handler-info`

| Query | 类型 | 默认 | 说明 |
|------|------|------|------|
| `page` | Integer | 1 | 页码 |
| `pageSize` | Integer | 3 | 每页条数 |
| `sort` | String | `bid_handler_id desc` | 排序 |

```bash
curl "http://{host}:{port}/bid-handler-info?page=1&pageSize=10&sort=bid_handler_id%20desc"
```

### 3.6 `POST /bid-handler-info/batch-delete`

**请求体**（`DeleteRequest`）：

```json
{ "ids": [1, 2, 3] }
```

返回 200 + 实际删除的实体列表。

---

## 4. 招标公告（InvitationForBid，World Bank 术语）

对应表 `invitation_for_bid`，主键 `invitation_id`。

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| POST | `/invitation-for-bid/add` | 创建 | `Result<InvitationForBid>` |
| POST | `/invitation-for-bid/batch-add` | 批量新增 | `Result<List<InvitationForBid>>` |
| GET | `/invitation-for-bid/{id}` | 按主键查询 | `Result<InvitationForBid>` |
| PATCH | `/invitation-for-bid/{id}` | 部分更新（18 字段） | `Result<Diff<InvitationForBid>>` |
| DELETE | `/invitation-for-bid/{id}` | 按主键删除 | `Result<Void>` |
| GET | `/invitation-for-bid` | 列表查询（分页） | `Result<PageInfo<InvitationForBid>>` |
| POST | `/invitation-for-bid/batch-delete` | 批量删除 | `Result<List<InvitationForBid>>` |
| POST | `/invitation-for-bid/status-update` | 状态变更 | `Result<InvitationForBid>` |

### 4.1 `POST /invitation-for-bid/add`

**请求体**（`AddRequest`，18 字段：3 必填 + 15 可选）：

**必填（3 字段）**：

| 字段 | 类型 | 校验 |
|------|------|------|
| `bidHandlerId` | Integer | @NotNull |
| `projectNo` | String | @NotBlank, @Size(max=50) |
| `purchaseTaskNo` | String | @NotBlank, @Size(max=50) |

**可选（15 字段）**：

| 字段 | 类型 | 校验 |
|------|------|------|
| `platformCategory` | String | @Size(max=50) |
| `tenderProcess` | String | @Size(max=50) |
| `tenderMethod` | String | @Size(max=50) |
| `purchaseMethod` | String | @Size(max=50) |
| `isFixedPrice` | Boolean | — |
| `hasDocumentFee` | Boolean | — |
| `deposit` | BigDecimal | — |
| `creditReportRequired` | Boolean | — |
| `registrationDeadline` | LocalDateTime | — |
| `technicalTenderDeadline` | LocalDateTime | — |
| `businessTenderDeadline` | LocalDateTime | — |
| `notes` | String | — |
| `purchaseCompany` | String | @Size(max=100) |
| `purchaseDepartment` | String | @Size(max=100) |
| `tenderingParty` | String | @Size(max=100) |

> `createdAt` 由 DB 默认 `CURRENT_TIMESTAMP` 自动维护。`status` 由 Service 自动设为 `DRAFT`，不可通过创建接口指定。

```bash
curl -X POST http://{host}:{port}/invitation-for-bid/add \
  -H "Content-Type: application/json" \
  -d '{"bidHandlerId":1,"projectNo":"PRJ-001","purchaseTaskNo":"PT-001"}'
```

### 4.2 `GET /invitation-for-bid/{id}`

返回 200 + 实体 或 404 + null。

### 4.3 `PATCH /invitation-for-bid/{id}`

**请求体**（`UpdateRequest`，18 字段全可选，仅 `@Size` 校验）：

18 字段结构同 §4.1，全部仅含 `@Size(max=...)` 校验（无 `@NotBlank` / `@NotNull`），PATCH 语义下 null = 不修改。

> `status` 不支持通过 PATCH 修改，请使用 `POST /invitation-for-bid/status-update` 端点。

> 排除：`invitationId`（由路径传入）、`createdAt`（DB 维护）。

### 4.4 `DELETE /invitation-for-bid/{id}`

> ⚠️ **逻辑外键警告**：`bid_record.invitation_id` 是逻辑外键（DB 无 FK 约束），删除本表后投标记录可能变孤儿，应用层自行处理。

### 4.5 `GET /invitation-for-bid`

| Query | 类型 | 默认 |
|------|------|------|
| `page` | Integer | 1 |
| `pageSize` | Integer | 10 |
| `sort` | String | `invitation_id desc` |

### 4.6 `POST /invitation-for-bid/batch-delete`

```json
{ "ids": [1, 2, 3] }
```

### 4.7 `POST /invitation-for-bid/status-update`

变更招标公告状态。三个字段全部通过 RequestBody 传入。状态机由 `BidNoticeStatus.canTransitionTo()` 校验：

```
DRAFT → PUBLISHED → CLOSED → AWARDED
   │         │          │         │
   └─────────┴──────────┴─────────┴──▶ CANCELLED（终态）
```

**请求体**（`StatusUpdateRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| `invitationId` | Integer | ✅ | @NotNull | 招标公告 ID（v18 重命名自 `noticeId`） |
| `status` | `BidNoticeStatus` enum | ✅ | @NotNull | 目标状态：PUBLISHED / CLOSED / AWARDED / CANCELLED |
| `remark` | String | ❌ | @Size(max=255) | 变更备注 |

**响应**：`Result<InvitationForBid>`（变更后的实体，含最新 status）。

```bash
curl -X POST http://{host}:{port}/invitation-for-bid/status-update \
  -H "Content-Type: application/json" \
  -d '{"invitationId":1,"status":"PUBLISHED","remark":"发布招标公告"}'
```

---

## 5. 资信文件上传（CredentialFile）

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| POST | `/credential-files/upload` | 一次性上传当前批次的全部原始资信文件并落盘 | `Result<UploadResponse>` |
| DELETE | `/credential-files/{uploadBatchId}/files/{fileId}` | 删除未提交文件 | `Result<Void>`（当前占位） |

### 5.1 `POST /credential-files/upload`

请求类型为 `multipart/form-data`。多个文件使用同一个 `files` 字段重复提交：

| Form 字段 | 类型 | 必填 | 说明 |
|-----------|------|:---:|------|
| `bidRecordId` | Integer | ✅ | 文件所属的投标记录 ID |
| `files` | File，可重复 | ✅ | 本次请求中的原始资信文件；单文件最大 150MB |
| `uploadBatchId` | — | — | 不由请求方传入，由后端为本次上传请求生成 |

```bash
curl -X POST http://localhost:8080/credential-files/upload \
  -F 'bidRecordId=1001' \
  -F 'files=@/path/to/license.pdf' \
  -F 'files=@/path/to/qualification.docx'
```

成功响应：

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "uploadBatchId": "550e8400-e29b-41d4-a716-446655440000",
    "files": [
      {
        "fileOrder": 0,
        "originalFilename": "license.pdf",
        "contentType": "application/pdf",
        "fileSize": 102400,
        "sha256": "64位小写十六进制摘要",
        "originalFilePath": "bid-records/8/batches/550e8400-e29b-41d4-a716-446655440000/original/文件UUID_license.pdf",
        "convertStatus": "UPLOADED"
      }
    ]
  }
}
```

**存储行为**：

- 开发环境根目录默认为 `/Users/lujianshi/Desktop/projects/MinIO`，可用 `CREDENTIAL_FILE_LOCAL_ROOT` 覆盖。
- 物理目录为 `{root}/bid-records/{bidRecordId}/batches/{uploadBatchId}/original/`，文件名为 `{fileUuid}_{安全原文件名}`。
- API 只返回相对路径，不暴露环境绝对路径。
- SHA-256 在落盘后重新读取实际文件计算。
- 同一次请求任一步失败时，补偿删除本次请求已经创建的文件；批次不会封口。
- 全部文件成功后，后端将 `credential_file_batch.status` 更新为 `UPLOADED`，当前批次不可继续追加。
- 每个文件成功验证后立即写入 `credential_file`；`fileOrder` 表示该批次内的文件顺序。

**错误响应**：

| 场景 | code | message |
|------|:---:|---------|
| 文件集合为空、空文件 | 400 | `上传文件不能为空` |
| 原始文件名为空 | 400 | `原始文件名不能为空` |
| 单文件超过 150MB 或请求总体超过 200MB | 400 | `文件大小超过限制` |
| 文件系统或摘要计算失败 | 500 | `服务器内部错误` |

> **当前阶段边界**：接口已经完成原始文件落盘、完整性校验和 `credential_file` 写入，但不会自动触发 Python Markdown 转换；转换由下方独立入口负责。

### 5.2 `POST /credential-files/bid-records/{bidRecordId}/convert`

无差别转换指定投标记录下的全部资信文件批次。Java 按批次逐文件调用 Python
`/convert/object`，Python 从 MinIO 读取 `original` 对象并将 Markdown 写入同级
`converted` 路径；Java 负责更新文件、批次状态，并将已完成的 Markdown object key
列表回写 `bid_record.credentials`。

重复调用会重新转换并覆盖对应的 `converted` 对象，不根据当前状态跳过文件。

```bash
curl -X POST http://localhost:8080/credential-files/bid-records/1001/convert
```

返回结果包含投标记录级汇总状态和每个批次的转换状态：

```json
{
  "code": 200,
  "data": {
    "bidRecordId": 1001,
    "status": "ALL_CONVERTED",
    "totalBatchCount": 1,
    "convertedBatchCount": 1,
    "processingBatchCount": 0,
    "failedBatchCount": 0,
    "batches": []
  }
}
```

文件转换失败时，文件和批次分别记录 `FAILED` 状态及错误信息；已经成功转换的文件
仍保留其 `CONVERTED` 状态和 Markdown 路径。

### 5.3 `DELETE /credential-files/{uploadBatchId}/files/{fileId}`

当前仅用于前后端联调，打印删除成功并返回：

```json
{
  "code": 200,
  "message": "删除成功（占位）",
  "data": null
}
```

当前不会删除物理文件或数据库记录。真实删除、批次归属校验和已绑定文件保护留到后续阶段。

---

## 6. 投标记录（BidRecord）

对应表 `bid_record`，主键 `bid_record_id`。（v18 由 `tenderer_detail` 合并而来）

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| POST | `/bid-record/add` | 创建（服务端算 bid_no） | `Result<BidRecord>` |
| GET | `/bid-record/{id}` | 按主键查询 | `Result<BidRecord>` |
| PATCH | `/bid-record/{id}` | 部分更新（4 字段） | `Result<Diff<BidRecord>>` |
| DELETE | `/bid-record/{id}` | 按主键删除 | `Result<Void>` |
| GET | `/bid-record` | 列表查询（分页） | `Result<PageInfo<BidRecord>>` |
| **GET** | **`/bid-record/by-invitation/{invitationId}`** | **按招标公告查询（不分页）** | **`Result<List<BidRecord>>`** |
| POST | `/bid-record/batch-delete` | 批量删除 | `Result<List<BidRecord>>` |
| POST | `/bid-record/analyze-risk/{id}` | AI 风险分析（同步） | `Result<RiskAnalysis>` |

> `PATCH` 不开放修改 `bidRecordId`/`invitationId`/`bidNo`/`bidAt`（主键与外键、投标次数、投标时间由服务端维护）。
> `bidNo` 服务端按 `(invitationId, bidderName)` 自动计算 `max(bid_no)+1`，并发同号场景内置 3 次重试 + UNIQUE 兜底。

### 6.1 `POST /bid-record/add`

**请求体**（`AddRequest`，4 字段必填 + 1 可选）：

| 字段 | 类型 | 校验 | 说明 |
|------|------|------|------|
| `invitationId` | Integer | @NotNull | 招标公告 ID（v18 必填，定位投标的标的） |
| `bidderName` | String | @NotBlank, @Size(max=100) | 投标人名称（v18 重命名自 `tendererName`） |
| `quotePrice` | BigDecimal | @NotNull | 报价（元） |
| `bidderContact` | String | @NotBlank, @Size(max=255) | 联系方式（v18 重命名自 `tendererContact`） |
| `credentials` | `List<String>` | 可选 | 转换后 Markdown 文件路径数组，顺序与文件顺序一致 |

**服务端自动设置**：
- `bidNo` = `MAX(bid_no) WHERE invitationId AND bidderName + 1`（首次为 1）
- `bidAt` = 当前时间

```bash
curl -X POST http://{host}:{port}/bid-record/add \
  -H "Content-Type: application/json" \
  -d '{"invitationId":1,"bidderName":"ABC公司","quotePrice":1234567.89,"bidderContact":"abc@example.com"}'
```

### 6.2 `GET /bid-record/{id}`

返回 200 + 实体 或 404 + null。

### 6.3 `GET /bid-record/by-invitation/{invitationId}`

按招标公告 ID 查询所有投标记录，**不分页**，按 `bid_at ASC` 排序（按投标时间正序展示评标过程）。

| Path 参数 | 类型 | 必填 |
|----------|------|:---:|
| `invitationId` | Integer | ✅ |

返回 200 + `List<BidRecord>`；无匹配记录时 `data = []`。当前实现不额外校验招标公告是否存在。

```bash
curl "http://{host}:{port}/bid-record/by-invitation/1"
```

### 6.4 `PATCH /bid-record/{id}`

**请求体**（`UpdateRequest`，4 字段全可选）：

| 字段 | 类型 | 校验 |
|------|------|------|
| `bidderName` | String | @Size(max=100) |
| `quotePrice` | BigDecimal | — |
| `bidderContact` | String | @Size(max=255) |
| `credentials` | `List<String>` | 可选；`null` 表示不修改 |

> 不允许修改：`bidRecordId`、`invitationId`、`bidNo`、`bidAt`。

```bash
curl -X PATCH http://{host}:{port}/bid-record/1 \
  -H "Content-Type: application/json" \
  -d '{"quotePrice":999999.99}'
```

### 6.5 `DELETE /bid-record/{id}`

返回 200 + null 或 404 + null。

### 6.6 `GET /bid-record`

| Query | 类型 | 默认 |
|------|------|------|
| `page` | Integer | 1 |
| `pageSize` | Integer | 10 |
| `sort` | String | `bid_record_id desc` |

### 6.7 `POST /bid-record/batch-delete`

```json
{ "ids": [1, 2, 3] }
```

### 6.8 `POST /bid-record/analyze-risk/{id}`

**同步调用 OpenAI 兼容 LM Studio**。服务端先联查并校验当前投标记录下的所有批次和文件均为
`CONVERTED`，再按 `batch_no`、`file_order` 顺序读取 converted Markdown，合并并写入
`bid-records/{bidRecordId}/merged/credentials.md`，最后将汇总文本一次性提交给大模型，解析 JSON，落库
`risk_analysis` 表。分析开始前会原子抢占 `bid_record` 的分析状态；同一投标记录已有分析处于执行中时，
不会再次调用大模型，返回业务状态码 `409`。分析完成后允许再次手动分析，并保留历史记录。

**响应**：`Result<RiskAnalysis>`（含 9 字段：analysisId / bidRecordId / bidderName / riskLevel / summary / suggestions / rawResponse / promptVersion / analyzedAt）。

```bash
curl -X POST http://{host}:{port}/bid-record/analyze-risk/1
```

---

## 7. 风险分析记录查询（RiskAnalysis）

对应表 `risk_analysis`，主键 `analysis_id`。**敏感数据**：历史查询为只读，风险分析触发由
`RiskAnalysisController` 暴露单独的 POST 入口。

> **写操作权限边界**：
> - 新增路径：`RiskAnalysisController.analyzeRisk`（状态抢占、状态校验、Markdown 汇总、AI 触发）→ `RiskAnalysisService.persistAndComplete` → `risk_analysis` 表
> - 删除路径：禁止通过本系统，改走数据库手工操作
> - 修改路径：禁止通过本系统（同上）

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| GET | `/risk-analysis` | 分页查询全部历史风险分析记录 | `Result<PageInfo<RiskAnalysis>>` |
| GET | `/risk-analysis/by-bid-record/{id}` | 按投标记录 ID 查询所有历史风险分析记录 | `Result<List<RiskAnalysis>>` |

### 7.1 `GET /risk-analysis`

| Query | 类型 | 默认 | 说明 |
|-------|------|------|------|
| `page` | Integer | 1 | 页码 |
| `pageSize` | Integer | 10 | 每页条数 |
| `sort` | String | `analysis_id desc` | 排序字段（如 `analyzed_at desc`） |

**排序建议值**：

| sort 值 | 含义 |
|--------|------|
| `analysis_id desc` | 按主键倒序（推荐默认，自增主键索引利用最高） |
| `analysis_id asc` | 按主键升序 |
| `analyzed_at desc` | 按分析时间倒序（最新在前） |
| `analyzed_at asc` | 按分析时间升序 |
| `risk_level asc, analysis_id desc` | 多字段联合（按风险等级排序后再按主键去重稳定排序） |

**PageInfo 主要字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `pageNum` | Integer | 当前页 |
| `pageSize` | Integer | 每页大小 |
| `size` | Integer | 当前页实际条数 |
| `total` | Integer | 总记录数 |
| `pages` | Integer | 总页数 |
| `list` | List<RiskAnalysis> | 当前页数据 |

**响应数据字段（每条 `RiskAnalysis`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `analysisId` | Integer | 主键 |
| `bidRecordId` | Integer | 关联的投标记录 ID（逻辑外键） |
| `bidderName` | String | 投标人名称（冗余字段，避免连表） |
| `riskLevel` | String | 风险等级：`LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |
| `summary` | String | 风险摘要（200 字以内） |
| `suggestions` | String | 建议措施（100 字以内） |
| `rawResponse` | String | 大模型原始响应（解析失败时供排查） |
| `promptVersion` | String | 使用的 prompt 版本号 |
| `analyzedAt` | LocalDateTime | 分析时间 |

```bash
curl "http://{host}:{port}/risk-analysis?page=1&pageSize=10&sort=analysis_id%20desc"
```

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "pageNum": 1,
    "pageSize": 10,
    "size": 10,
    "total": 47,
    "pages": 5,
    "list": [
      {
        "analysisId": 47,
        "bidRecordId": 2,
        "bidderName": "示例公司",
        "riskLevel": "HIGH",
        "summary": "...",
        "suggestions": "...",
        "rawResponse": "{...}",
        "promptVersion": "v1",
        "analyzedAt": "2026-07-06T09:53:22"
      }
    ]
  }
}
```

### 7.2 `GET /risk-analysis/by-bid-record/{id}`

> ⚠️ **v0.3 路径迁移**:旧路径 `GET /bid-record/risk-history/{id}` 已经从 `BidRecordController` 移除(归到 `RiskAnalysisController` 域名下,详见 commit)。前端需切到新路径,否则 404。

查询指定投标记录 ID 的所有历史风险分析记录(按 `analyzedAt DESC`)。

| Path 参数 | 类型 | 必填 |
|----------|------|:---:|
| `id` | Integer | ✅ |

**响应**:`Result<List<RiskAnalysis>>`,无记录时 `data = []`(不是 null)。

```bash
curl "http://{host}:{port}/risk-analysis/by-bid-record/1"
```

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "analysisId": 47,
      "bidRecordId": 2,
      "bidderName": "示例公司",
      "riskLevel": "MEDIUM",
      "summary": "...",
      "suggestions": "...",
      "rawResponse": "{...}",
      "promptVersion": "v1",
      "analyzedAt": "2026-07-08T09:53:22"
    }
  ]
}
```



---

## 附录 A：业务码速查

| code | 触发条件 |
|------|----------|
| 200 | 业务正常完成 |
| 400 | 参数校验失败 / 分页参数错误 |
| 404 | 按主键查询/更新/删除时记录不存在 |
| 500 | 异常未捕获（`GlobalExceptionHandler` 统一处理） |

## 附录 B：v18 命名变更摘要

变更范围：表 / 列 / 类 / 包 / 端点路径 / AI prompt 占位符。

| 维度 | v17 | v18 |
|------|-----|-----|
| 表 | `tender_handler_info` | `bid_handler_info` |
| 表 | `tender_notice` | `invitation_for_bid` |
| 表 | `tender_notice_status_history` | `invitation_for_bid_status_history` |
| 表 | `tenderer_detail` | `bid_record` |
| PK | `tender_handler_id` | `bid_handler_id` ^[PK 列重命名实际在 V19 完成，V18 仅重命名了表名] |
| PK | `notice_id` | `invitation_id` |
| PK | `tenderer_id` | `bid_record_id` |
| 业务字段 | `tenderer_name` | `bidder_name` |
| 业务字段 | `tenderer_contact` | `bidder_contact` |
| FK | `tenderer_detail.tenderer_id` (`risk_analysis`) | `bid_record.bid_record_id` |
| 新增列 | — | `bid_record.invitation_id` / `bid_no` / `bid_at` |
| 约束 | — | `UNIQUE (invitation_id, bidder_name, bid_no)` |
| 端点 | `/tender-handler-info/...` | `/bid-handler-info/...` |
| 端点 | `/tender-notice/...` | `/invitation-for-bid/...` |
| 端点 | `/tenderer-detail/...` | `/bid-record/...` |
| 端点 | `/add-tenderer-detail` | `/bid-record/add` |
| 端点 | `/tenderer-detail/by-notice/{id}` | `/bid-record/by-invitation/{id}` |
| AI 占位符 | `{{tendererName}}` | `{{bidderName}}` |
| AI 占位符 | `{{tendererContact}}` | `{{bidderContact}}` |
| Mail DTO | `BidWinningListItem.tendererName` | `BidWinningListItem.bidderName` |

## 附录 C：相关文档

- 内部架构：[`docs/architecture.md`](architecture.md)
- 数据库结构：[`docs/database.md`](database.md)
- 项目约定：[`AGENTS.md`](../AGENTS.md)
