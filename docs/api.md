# API 文档（bid-winning-backend）

> 本文档描述 bid-winning-backend 提供的全部对外 HTTP 接口。共 23 端点（含 2 AI 风险分析端点）。
>
> 内部架构：[`docs/architecture.md`](architecture.md) · 数据库结构：[`docs/database.md`](database.md)

---

## 0. 文档元信息

**Base URL**：`http://{host}:{port}`

- dev 默认：`http://localhost:8080`
- prod：`http://{prod-host}:80`

**请求体**：`application/json; charset=utf-8`（所有端点统一）

**字符编码**：UTF-8

### 端点速览

| 模块 | 端点数 | 路径前缀 |
|------|:---:|---------|
| 服务状态 + 邮件测试 | 3 | `/service-status`、`/send-email`、`/send-notification` |
| 经办人 (BidHandlerInfo) | 6 | `/bid-handler-info/...` |
| 定标通知 (TenderNotice) | 6 | `/tender-notice/...` |
| 供应商明细 (SupplierDetail) | 6 | `/supplier-detail/...` |
| **AI 风险分析**（v0.1+） | 2 | `/supplier-detail/{action}/{id}` |

**AI 风险分析端点**（详细见 Controller 源码）：

| Method | Path | 说明 |
|--------|------|------|
| POST | `/supplier-detail/analyze-risk/{id}` | 同步调 DeepSeek，解析 JSON，落库，返全字段 `RiskAnalysis` |
| GET | `/supplier-detail/risk-history/{id}` | 历史分析记录（按 `analyzedAt` 倒序） |

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
- `sort` 格式：PageHelper 原生（空格分隔），例 `"notice_id desc"`
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
|----------|----------|---------|
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
| 其他 17 个端点 | — | 未发送 |

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
| `bidWinningList[].supplierName` | String | ✅ | 供应商名称 |
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
    "templateName": "tender-notice",
    "bidWinningList": [
      { "supplierName": "A供应商", "bidWinningAmount": "67500.00" }
    ]
  }'
```

---

## 3. 经办人（BidHandlerInfo）

对应表 `bid_handler_info`，主键 `handler_id`。

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| POST | `/bid-handler-info/add` | 创建 | `Result<BidHandlerInfo>` |
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
| `sort` | String | `handler_id desc` | 排序 |

```bash
curl "http://{host}:{port}/bid-handler-info?page=1&pageSize=10&sort=handler_id%20desc"
```

### 3.6 `POST /bid-handler-info/batch-delete`

**请求体**（`DeleteRequest`）：

```json
{ "ids": [1, 2, 3] }
```

返回 200 + 实际删除的实体列表。

---

## 4. 定标通知（TenderNotice）

对应表 `tender_notice`，主键 `notice_id`。

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| POST | `/tender-notice/add` | 创建 | `Result<TenderNotice>` |
| GET | `/tender-notice/{id}` | 按主键查询 | `Result<TenderNotice>` |
| PATCH | `/tender-notice/{id}` | 部分更新（17 字段） | `Result<Diff<TenderNotice>>` |
| DELETE | `/tender-notice/{id}` | 按主键删除 | `Result<Void>` |
| GET | `/tender-notice` | 列表查询（分页） | `Result<PageInfo<TenderNotice>>` |
| POST | `/tender-notice/batch-delete` | 批量删除 | `Result<List<TenderNotice>>` |

### 4.1 `POST /tender-notice/add`

**请求体**（`AddRequest`，17 字段：3 必填 + 14 可选）：

**必填（3 字段）**：

| 字段 | 类型 | 校验 |
|------|------|------|
| `handlerId` | Integer | @NotNull |
| `projectNo` | String | @NotBlank, @Size(max=50) |
| `purchaseTaskNo` | String | @NotBlank, @Size(max=50) |

**可选（14 字段）**：

| 字段 | 类型 | 校验 |
|------|------|------|
| `platformCategory` | String | @Size(max=50) |
| `biddingProcess` | String | @Size(max=50) |
| `biddingMethod` | String | @Size(max=50) |
| `purchaseMethod` | String | @Size(max=50) |
| `isFixedPrice` | Boolean | — |
| `targetCost` | BigDecimal | — |
| `documentFee` | BigDecimal | — |
| `deposit` | BigDecimal | — |
| `creditReportRequired` | Boolean | — |
| `registrationDeadline` | LocalDateTime | — |
| `techBidDeadline` | LocalDateTime | — |
| `businessBidDeadline` | LocalDateTime | — |
| `biddingStatus` | String | @Size(max=20) |
| `notes` | String | — |

> `createdAt` 由 DB 默认 `CURRENT_TIMESTAMP` 自动维护。

```bash
curl -X POST http://{host}:{port}/tender-notice/add \
  -H "Content-Type: application/json" \
  -d '{"handlerId":1,"projectNo":"PRJ-001","purchaseTaskNo":"PT-001"}'
```

### 4.2 `GET /tender-notice/{id}`

返回 200 + 实体 或 404 + null。

### 4.3 `PATCH /tender-notice/{id}`

**请求体**（`UpdateRequest`，17 字段全可选，仅 `@Size` 校验）：

17 字段结构同 §4.1，全部仅含 `@Size(max=...)` 校验（无 `@NotBlank` / `@NotNull`），PATCH 语义下 null = 不修改。

> 排除：`noticeId`（由路径传入）、`createdAt`（DB 维护）。

```bash
curl -X PATCH http://{host}:{port}/tender-notice/1 \
  -H "Content-Type: application/json" \
  -d '{"biddingStatus":"已完成"}'
```

### 4.4 `DELETE /tender-notice/{id}`

> ⚠️ **逻辑外键警告**：`supplier_detail.notice_id` 是逻辑外键（DB 无 FK 约束），删除本表后明细行可能变孤儿，应用层自行处理。

### 4.5 `GET /tender-notice`

| Query | 类型 | 默认 |
|------|------|------|
| `page` | Integer | 1 |
| `pageSize` | Integer | 10 |
| `sort` | String | `notice_id desc` |

### 4.6 `POST /tender-notice/batch-delete`

```json
{ "ids": [1, 2, 3] }
```

---

## 5. 供应商明细（SupplierDetail）

对应表 `supplier_detail`，主键 `supplier_id`。

| Method | Path | 用途 | 返回 |
|--------|------|------|------|
| POST | `/add-supplier-detail` | 创建 | `Result<SupplierDetail>` |
| GET | `/supplier-detail/{id}` | 按主键查询 | `Result<SupplierDetail>` |
| PATCH | `/supplier-detail/{id}` | 部分更新 | `Result<Diff<SupplierDetail>>` |
| DELETE | `/supplier-detail/{id}` | 按主键删除 | `Result<Void>` |
| GET | `/supplier-detail` | 列表查询（分页） | `Result<PageInfo<SupplierDetail>>` |
| POST | `/supplier-detail/batch-delete` | 批量删除 | `Result<List<SupplierDetail>>` |

### 5.1 `POST /add-supplier-detail`

**请求体**（`AddRequest`，3 字段必填）：

| 字段 | 类型 | 校验 |
|------|------|------|
| `supplierName` | String | @NotBlank, @Size(max=100) |
| `quotePrice` | BigDecimal | @NotNull |
| `supplierContact` | String | @NotBlank, @Size(max=255) |

```bash
curl -X POST http://{host}:{port}/add-supplier-detail \
  -H "Content-Type: application/json" \
  -d '{"supplierName":"ABC公司","quotePrice":1234567.89,"supplierContact":"abc@example.com"}'
```

### 5.2 `GET /supplier-detail/{id}`

返回 200 + 实体 或 404 + null。

### 5.3 `PATCH /supplier-detail/{id}`

**请求体**（`UpdateRequest`，3 字段全可选）：

| 字段 | 类型 | 校验 |
|------|------|------|
| `supplierName` | String | @Size(max=100) |
| `quotePrice` | BigDecimal | — |
| `supplierContact` | String | @Size(max=255) |

```bash
curl -X PATCH http://{host}:{port}/supplier-detail/1 \
  -H "Content-Type: application/json" \
  -d '{"quotePrice":999999.99}'
```

### 5.4 `DELETE /supplier-detail/{id}`

返回 200 + null 或 404 + null。

### 5.5 `GET /supplier-detail`

| Query | 类型 | 默认 |
|------|------|------|
| `page` | Integer | 1 |
| `pageSize` | Integer | 10 |
| `sort` | String | `supplier_id desc` |

### 5.6 `POST /supplier-detail/batch-delete`

```json
{ "ids": [1, 2, 3] }
```

---

## 附录 A：业务码速查

| code | 触发条件 |
|------|----------|
| 200 | 业务正常完成 |
| 400 | 参数校验失败 / 分页参数错误 |
| 404 | 按主键查询/更新/删除时记录不存在 |
| 500 | 异常未捕获（`GlobalExceptionHandler` 统一处理） |

## 附录 B：相关文档

- 内部架构：[`docs/architecture.md`](architecture.md)
- 数据库结构：[`docs/database.md`](database.md)
- 项目约定：[`AGENTS.md`](../AGENTS.md)
