# API 文档（bid-winning-backend）

> 本文档描述了 `bid-winning-backend` 提供的全部对外 HTTP 接口。
>
> 内部架构、表结构、扩展开发流程等内容不在本文档范围，分别见 [architecture.md](architecture.md) / [database.md](database.md) / [development-flow.md](development-flow.md)。

---

## 0. 文档元信息

| 项 | 值 |
| --- | --- |
| Base URL（dev） | `http://localhost:8080` |
| Base URL（test） | `http://localhost:8080` |
| Base URL（prod） | `http://<host>`（端口 80） |
| Content-Type | `application/json; charset=utf-8`（除 `/send-email` 使用 `application/x-www-form-urlencoded`） |
| 字符编码 | UTF-8 |
| API 版本 | v1（当前） |
| 适用后端版本 | 0.1.0-SNAPSHOT |
| 最后更新 | 2026-06-15 |

### 0.1 接口速览

| 模块 | 端点数 | 说明 |
| --- | --- | --- |
| 演示 | 3 | `GET /hello`、`GET /access-url`、`POST /fetch-url` |
| 邮件 | 2 | `POST /send-email`（纯文本）、`POST /send-notification`（HTML 模板） |
| 经办人 | 6 | `bid_handler_info` 表 CRUD + 批量删除 |
| 定标通知 | 6 | `bid_winning_notification` 表 CRUD + 批量删除 |
| 中标结果明细 | 6 | `bid_winning_supplier_detail` 表 CRUD + 批量删除 |
| **合计** | **23** | |

---

## 1. 通用约定

### 1.1 统一响应结构（Result）

所有业务端点（演示/邮件除外）统一返回 `Result<T>` 包装结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": <T>
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | Integer | 业务状态码，详见 §1.2 |
| `message` | String | 文字消息（前端可直接展示） |
| `data` | T（泛型） | 业务数据载荷，无数据时为 `null` |

**data 类型与端点对应**：

| 端点类型 | data 类型 | 备注 |
| --- | --- | --- |
| 创建 | `Entity` | 含自增 ID |
| 查询（按主键） | `Entity` 或 `null`（404） | |
| 部分更新 | `Diff<Entity>` | 见 §1.3 |
| 删除 | `null` | |
| 列表查询 | `PageInfo<Entity>` | 见 §1.5 |
| 批量删除 | `List<Entity>` | 实际删除的实体列表（见 §1.4） |

### 1.2 业务状态码

| code | 含义 | 触发场景 |
| --- | --- | --- |
| 200 | 成功 | 业务正常完成 |
| 400 | 参数错误 | 分页参数错误、`@NotBlank`/`@Size` 校验失败等 |
| 404 | 资源不存在 | 按主键查询/更新/删除时记录不存在 |
| 500 | 服务器内部错误 | 异常未捕获（待 `GlobalExceptionHandler` 统一处理） |

> 邮件端点（`/send-email`、`/send-notification`）**不**返回 `Result<T>`，直接返回字符串或 void（详见各端点章节）。

### 1.3 PATCH 语义（部分更新）

`PATCH /{resource}/{id}` 端点遵循 RFC 5789 语义：

- 调用方传哪些字段就更新哪些字段
- 未传的字段（`null`）在 DB 中保持原值
- 由 XML 动态 SQL（`<set> + <if test="xxx != null">`）实现
- **不**支持"将某字段置空"（与 PATCH 标准限制一致）
- 响应 `data` 为 `Diff<Entity>` 包装，含 `before`（DB 原值）与 `after`（更新后）

**Diff 结构**：

```json
{
  "before": { /* 字段更新前的值 */ },
  "after":  { /* 字段更新后的值 */ }
}
```

### 1.4 批量删除约定

`POST /{resource}/batch-delete` 端点：

- 入参：`{ "ids": [1, 2, 3] }`（1-500 条）
- **永远返回 200**，`data` = 实际删除的实体列表（按 DB 物理顺序）
- 列表中**不存在**的 ID 不会出现在 `data` 中
- 前端 diff 用法：`missing = requested.filter(id => !data.map(e => e.id).includes(id))`
- 业务约束：单次最多 500 条（`@Size(max=500)` 校验）

**注意**：批量删除**不做**事务封装、不做级联删除、不保证外键引用完整性。删除有外键引用的记录时，子表相关行会变孤儿（应用层需自行处理）。

### 1.5 分页约定

列表查询端点（`GET /{resource}`）：

- Query 参数：`pageNum`（默认 1）、`pageSize`（默认 10）、`sort`（默认见各端点）
- `sort` 格式：PageHelper 原生格式（空格分隔），例 `"notification_id desc"`
- 异常处理：参数错误返 `code=400, message="分页查询参数错误"`

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

### 1.6 时间/金额格式

| 字段类型 | 格式 | 示例 |
| --- | --- | --- |
| 时间（DATETIME） | ISO-8601 字符串 | `"2026-06-15T14:30:00"` |
| 金额（DECIMAL） | JSON 数字（BigDecimal 序列化） | `67500.00` |
| 布尔（TINYINT(1)） | JSON 布尔 | `true` / `false` |

### 1.7 业务操作后邮件通知（AOP 切面）

业务 Controller 端点标注 `@SendEmailOnSuccess(to = "...")` 后，AOP 切面（`CrudResultEmailAspect`）会在方法正常返回后**异步**发送邮件通知。

**触发条件**：
- 方法正常返回（异常不触发）
- 返回值必须是 `Result<T>` 类型（切面做 `instanceof` 校验）

**当前已启用端点**：3 个业务 Controller × 6 端点 = 18 处
- `BidHandlerInfoController`：create / getById / patch / delete / list / batchDelete
- `BidWinningNotificationController`：create / getById / patch / delete / list / batchDelete
- `BidWinningSupplierDetailController`：create / getById / patch / delete / list / batchDelete

**邮件内容**（HTML 模板 `crud-result-notification.html`）：
- 操作类型徽章（CREATE / UPDATE / DELETE / BATCH_DELETE，颜色区分）
- 业务码（`Result.code`） + 消息（`Result.message`）
- 返回数据 JSON（`Result.data` 经 fastjson2 序列化）
- 接口路径（HTTP method + 类名#方法名）
- 时间戳

**示例注解**：

```java
@SendEmailOnSuccess(to = "ops@company.com")
@PostMapping("/create-bid-handler-info")
public Result<BidHandlerInfo> createBidHandlerInfo(@RequestBody BidHandlerInfoCreateRequest request) {
    // 业务代码... 成功后自动发邮件
}
```

**注解参数**：
- `to`（必填）：收件人邮箱
- `subject`（可选）：自定义邮件主题，默认 `[业务操作通知] {endpoint} {operation} - {timestamp}`
- `templateName`（可选）：Thymeleaf 模板名，默认 `crud-result`

**性能与可靠性**：
- 邮件发送在异步线程池中执行（`AsyncTaskManager.execute(...)`），不阻塞接口响应
- 邮件失败仅日志告警（`System.err.println`），**不影响**业务结果

**响应时间（主线程 vs 后台子线程分离）**：

接口响应时间 ≈ 主线程耗时 ≈ **微秒级**（不含邮件发送），因为：
- AOP 切面方法中 `AsyncTaskManager.execute(...)` 内部仅 `new Thread(...).start()`（**立即返回**）
- 实际 SMTP 发送（1-3 秒）发生在**后台子线程**，与主线程完全并行
- 前端在邮件发送完成前就已收到 200 OK 响应

| 模式 | 主线程耗时 | 接口响应 |
|------|----------|---------|
| 同步发送（不用 AsyncTaskManager） | 1-3 秒（SMTP 阻塞主线程）| 慢 |
| **异步发送（当前实现）** | **~微秒级** | **快（200 OK）** |

**详细时序**：
- Step 1：Controller 业务代码执行，return Result
- Step 2：触发 @AfterReturning 切面（主线程未放行）
  - Step 2.1：切面前半段（~微秒级，instanceof 验证 + 反射 + JSON 序列化 + 主题）
  - Step 2.2：`AsyncTaskManager.execute(...)` 提交异步任务，主线程仅 `new Thread + .start()`，**瞬间**完成
- Step 3：切面方法全部执行完毕（return）
- Step 4：Spring 代理对象放行，Spring MVC 序列化 Result 为 JSON，**HTTP 响应完成**
- Step 5（**与 Step 3-4 并行**）：后台子线程执行 sleep(0) + 邮件发送全流程

完整执行链路：见 [docs/architecture.md §7.4](architecture.md#74-完整业务执行生命周期5-步精确时序--主子线程分离)
AOP 切面开发模式：见 [docs/development-flow.md §8](development-flow.md#8-aop-切面开发模式4-步标准开发)

**生产占位邮箱**：`ops@company.com` 为占位符，实际部署需替换为真实邮箱。

---

## 2. 演示接口

> 仅供开发调试使用，**不保证向前兼容**，生产环境可关闭。

### 2.1 `GET /hello`

**简述**：返回固定字符串

**请求**：无

**响应**：

```
Content-Type: text/plain;charset=UTF-8

Hello World!
```

---

### 2.2 `GET /access-url`

**简述**：重定向到 `application.yml` 中 `custom.url` 配置的 URL

**请求**：无

**响应**：HTTP 302 重定向

---

### 2.3 `POST /fetch-url`

**简述**：触发 3 秒延迟任务 + 立即返回配置的 URL

**请求**：无 body

**响应**：

```
Content-Type: text/plain;charset=UTF-8

https://baidu.com
```

**副作用**：调用 `HelloService.fetchAfterDelay()`，3 秒后在控制台打印"获取地址成功"。

---

## 3. 邮件接口

### 3.1 `POST /send-email`

**简述**：发送纯文本邮件（同步）

**Content-Type**：`application/x-www-form-urlencoded`

**请求参数**（form 表单）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `to` | String | ✅ | 收件人邮箱 |
| `subject` | String | ✅ | 邮件主题 |
| `content` | String | ✅ | 邮件正文（纯文本） |

> **注意**：`from` 字段由后端从 `spring.mail.username` 配置中读取（防发件人伪造），请求中传入的 `from` 被忽略。

**响应**：void（HTTP 200，body 为空）

**示例**：

```bash
curl -X POST http://localhost:8080/send-email \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "to=user@example.com&subject=测试&content=Hello"
```

---

### 3.2 `POST /send-notification`

**简述**：发送中标通知书 HTML 邮件（异步，延迟 5 秒）

**Content-Type**：`application/json`

**请求体**（`BidWinningMailRequest`，继承 `PlainTextMailRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- |----| --- |
| `to` | String | 是  | 收件人邮箱（继承自 PlainTextMailRequest） |
| `subject` | String | 是  | 邮件主题（继承） |
| `content` | String | 否  | 此端点不使用，由 HTML 模板替代 |
| `projectCode` | String | 是  | 项目编号（模板变量 `${projectCode}`） |
| `handlerName` | String | 是  | 经办人姓名（模板变量 `${handlerName}`） |
| `bidWinningList` | Array | 是  | 中标结果列表（模板 `th:each` 循环） |
| `bidWinningList[].supplierName` | String | 是  | 供应商名称 |
| `bidWinningList[].bidWinningAmount` | String | 是  | 中标价（字符串格式，如 `"67,500.00 RMB"`） |
| `templateName` | String | 是  | 模板文件名（不含 `.html` 后缀，如 `"bid-winning-notification"`） |

**响应**：

```
Content-Type: text/plain;charset=UTF-8

发送成功
```

**执行流程**：
1. 立即渲染 Thymeleaf 模板
2. 立即返回"发送成功"（主线程不阻塞）
3. 5 秒后异步发送邮件 + 打印 MD 格式日志

**示例**：

```bash
curl -X POST http://localhost:8080/send-notification \
  -H "Content-Type: application/json" \
  -d '{
    "to": "user@example.com",
    "subject": "中标通知书",
    "projectCode": "WB20260325017",
    "handlerName": "张三",
    "bidWinningList": [
      { "supplierName": "供应商A", "bidWinningAmount": "67,500.00 RMB" },
      { "supplierName": "供应商B", "bidWinningAmount": "83,000.00 RMB" }
    ],
    "templateName": "bid-winning-notification"
  }'
```

> **注意**：模板文件必须存在于 `src/main/resources/templates/{templateName}.html`，否则抛 `IllegalArgumentException`（本端点异常不返回 `Result`，直接抛 Spring 默认 500）。

---

## 4. 经办人（bid_handler_info）

> 资源：`bid_handler_info` 表
> 主键：`handler_id`（自增 INT）
> Controller：`BidHandlerInfoController`

### 4.1 `POST /create-bid-handler-info`

**简述**：创建经办人

**Content-Type**：`application/json`

**请求体**（`BidHandlerInfoCreateRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
| --- | --- |----| --- | --- |
| `handlerName` | String | 是  | `@NotBlank`, `@Size(max=50)` | 经办人姓名 |
| `companyName` | String | 是  | `@NotBlank`, `@Size(max=100)` | 所属采购公司 |
| `departmentName` | String | 是  | `@NotBlank`, `@Size(max=100)` | 所属部门 |

> **注意**：`@Valid` 校验当前**未启用**（待 `GlobalExceptionHandler` 补全），字段缺失不会触发 400。

**响应**：`Result<BidHandlerInfo>`

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "handlerId": 1,
    "handlerName": "张三",
    "companyName": "测试公司",
    "departmentName": "采购部"
  }
}
```

---

### 4.2 `GET /bid-handler-info/{id}`

**简述**：按主键查询单条经办人

**路径参数**：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Integer | 经办人主键 ID |

**响应**：
- 200：`Result<BidHandlerInfo>`
- 404：`Result<null>`，message="记录不存在"

---

### 4.3 `PATCH /bid-handler-info/{id}`

**简述**：按主键部分更新经办人（返回 before/after diff）

**Content-Type**：`application/json`

**请求体**（`BidHandlerInfoUpdateRequest`，3 字段全部可选）：

| 字段 | 类型 | 校验 | 说明 |
| --- | --- | --- | --- |
| `handlerName` | String | `@Size(max=50)` | 省略/为 null = 不修改 |
| `companyName` | String | `@Size(max=100)` | |
| `departmentName` | String | `@Size(max=100)` | |

**响应**：
- 200：`Result<Diff<BidHandlerInfo>>` 含 `before` + `after`
- 404：记录不存在

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "before": { "handlerId": 1, "handlerName": "张三", "companyName": "原公司", "departmentName": "原部门" },
    "after":  { "handlerId": 1, "handlerName": "李四", "companyName": "新公司", "departmentName": "新部门" }
  }
}
```

---

### 4.4 `DELETE /bid-handler-info/{id}`

**简述**：按主键删除经办人

**响应**：
- 200：`Result<null>`，message="删除成功"
- 404：记录不存在

---

### 4.5 `GET /bid-handler-info`

**简述**：分页查询经办人列表

**Query 参数**：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | Integer | 1 | 页码（1-based） |
| `size` | Integer | 3 | 每页条数 |
| `sort` | String | `handler_id desc` | 排序（PageHelper 原生格式，空格分隔） |

**响应**：
- 200：`Result<PageInfo<BidHandlerInfo>>`
- 400：分页参数错误

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "total": 8,
    "list": [
      { "handlerId": 8, "handlerName": "...", "companyName": "...", "departmentName": "..." }
    ],
    "pageNum": 1,
    "pageSize": 3,
    "pages": 3,
    "hasNextPage": true
  }
}
```

---

### 4.6 `POST /bid-handler-info/batch-delete`

**简述**：按主键 ID 列表批量删除

**Content-Type**：`application/json`

**请求体**（`BidHandlerInfoBatchDeleteRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
| --- | --- |----| --- | --- |
| `ids` | Array\<Integer\> | 是  | `@NotEmpty`, `@Size(max=500)` | 待删除主键 ID 列表（1-500 条） |

**响应**：

- 200：`Result<List<BidHandlerInfo>>`，`data` = 实际删除的实体列表（DB 物理顺序）
- 400：`@Valid` 校验失败（待 `GlobalExceptionHandler` 启用）

```json
{
  "code": 200,
  "message": "批量删除完成",
  "data": [
    { "handlerId": 2, "handlerName": "李四", "companyName": "...", "departmentName": "..." }
  ]
}
```

---

## 5. 定标通知（bid_winning_notification）

> 资源：`bid_winning_notification` 表
> 主键：`notification_id`（自增 INT）
> Controller：`BidWinningNotificationController`

### 5.1 `POST /create-bid-winning-notification`

**简述**：创建定标通知

**Content-Type**：`application/json`

**请求体**（`BidWinningNotificationCreateRequest`，17 字段）：

**必填字段（3 个）**：

| 字段 | 类型 | 校验 | 说明 |
| --- | --- | --- | --- |
| `handlerId` | Integer | `@NotNull` | 经办人 ID（关联 `bid_handler_info.handler_id`） |
| `projectNo` | String | `@NotBlank`, `@Size(max=50)` | 项目编号 |
| `purchaseTaskNo` | String | `@NotBlank`, `@Size(max=50)` | 采购任务编号 |

**可选字段（14 个）**：

| 字段 | 类型 | 校验 | 说明 |
| --- | --- | --- | --- |
| `platformCategory` | String | `@Size(max=50)` | 平台采购类别 |
| `biddingProcess` | String | `@Size(max=50)` | 招标流程 |
| `biddingMethod` | String | `@Size(max=50)` | 招标方式 |
| `purchaseMethod` | String | `@Size(max=50)` | 采购方式 |
| `isFixedPrice` | Boolean | — | 是否固定总价（1=是，0=否） |
| `targetCost` | BigDecimal | — | 目标成本（元） |
| `documentFee` | BigDecimal | — | 标书费 |
| `deposit` | BigDecimal | — | 保证金 |
| `creditReportRequired` | Boolean | — | 是否需要征信报告 |
| `registrationDeadline` | LocalDateTime | — | 报名截止时间 |
| `techBidDeadline` | LocalDateTime | — | 技术回标截止时间 |
| `businessBidDeadline` | LocalDateTime | — | 商务回标截止时间 |
| `biddingStatus` | String | `@Size(max=20)` | 状态（DB 默认 "已完成"） |
| `notes` | String | — | 备注 |

> **注意**：`createdAt` 由 DB 默认 `CURRENT_TIMESTAMP` 自动维护，请求中不传。

**响应**：`Result<BidWinningNotification>`（含自增 `notificationId` 与 `createdAt`）

---

### 5.2 `GET /bid-winning-notification/{id}`

**简述**：按主键查询单条定标通知

**响应**：
- 200：`Result<BidWinningNotification>`
- 404：记录不存在

---

### 5.3 `PATCH /bid-winning-notification/{id}`

**简述**：按主键部分更新定标通知（17 字段全部可选）

**请求体**（`BidWinningNotificationUpdateRequest`）：

17 字段结构同 §5.1，**全部**仅含 `@Size` 校验（无 `@NotBlank` / `@NotNull`），PATCH 语义下 null = 不修改。

> **排除**：`notificationId`（由路径传入）、`createdAt`（DB 维护，结构上不可改）

**响应**：
- 200：`Result<Diff<BidWinningNotification>>`
- 404：记录不存在

---

### 5.4 `DELETE /bid-winning-notification/{id}`

**简述**：按主键删除定标通知

**响应**：
- 200：删除成功
- 404：记录不存在

---

### 5.5 `GET /bid-winning-notification`

**简述**：分页查询定标通知列表

**Query 参数**：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pageNum` | Integer | 1 | 页码 |
| `pageSize` | Integer | 10 | 每页条数 |
| `sort` | String | `notification_id desc` | 排序 |

**响应**：
- 200：`Result<PageInfo<BidWinningNotification>>`
- 400：分页参数错误

---

### 5.6 `POST /bid-winning-notification/batch-delete`

**简述**：按主键 ID 列表批量删除（p3 升级版：data 返实际删除的实体列表）

**请求体**（`BidWinningNotificationBatchDeleteRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- |
| `ids` | Array\<Integer\> | ✅ | `@NotEmpty`, `@Size(max=500)` | 待删除 `notification_id` 列表 |

**响应**：
- 200：`Result<List<BidWinningNotification>>`，`data` = 实际删除的实体列表

> **逻辑外键警告**：`bid_winning_supplier_detail.notification_id` 是逻辑外键（DB 无 FK 约束）。删除本表后，明细行会变孤儿，应用层需自行处理。

---

## 6. 中标结果明细（bid_winning_supplier_detail）

> 资源：`bid_winning_supplier_detail` 表
> 主键：`detail_id`（自增 INT）
> Controller：`BidWinningSupplierDetailController`

### 6.1 `POST /create-bid-winning-supplier-detail`

**简述**：创建中标结果明细

**Content-Type**：`application/json`

**请求体**（`BidWinningSupplierDetailCreateRequest`，3 字段）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- |
| `notificationId` | Integer | ✅ | `@NotNull` | 定标通知 ID（关联 `bid_winning_notification.notification_id`） |
| `supplierName` | String | ✅ | `@NotBlank`, `@Size(max=100)` | 中标供应商名称 |
| `amount` | BigDecimal | ✅ | `@NotNull` | 中标价（元） |

**响应**：`Result<BidWinningSupplierDetail>`（含自增 `detailId`）

---

### 6.2 `GET /bid-winning-supplier-detail/{id}`

**简述**：按主键查询单条明细

**响应**：
- 200：`Result<BidWinningSupplierDetail>`
- 404：记录不存在

---

### 6.3 `PATCH /bid-winning-supplier-detail/{id}`

**简述**：按主键部分更新明细（3 字段全部可选）

**请求体**（`BidWinningSupplierDetailUpdateRequest`）：

3 字段结构同 §6.1，**全部**仅含 `@Size` 校验（PATCH 语义）。

**响应**：
- 200：`Result<Diff<BidWinningSupplierDetail>>`
- 404：记录不存在

---

### 6.4 `DELETE /bid-winning-supplier-detail/{id}`

**简述**：按主键删除明细

**响应**：
- 200：删除成功
- 404：记录不存在

---

### 6.5 `GET /bid-winning-supplier-detail`

**简述**：分页查询明细列表

**Query 参数**：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pageNum` | Integer | 1 | 页码 |
| `pageSize` | Integer | 10 | 每页条数 |
| `sort` | String | `detail_id desc` | 排序 |

**响应**：
- 200：`Result<PageInfo<BidWinningSupplierDetail>>`
- 400：分页参数错误

---

### 6.6 `POST /bid-winning-supplier-detail/batch-delete`

**简述**：按主键 ID 列表批量删除（p3 升级版）

**请求体**（`BidWinningSupplierDetailBatchDeleteRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- |
| `ids` | Array\<Integer\> | ✅ | `@NotEmpty`, `@Size(max=500)` | 待删除 `detail_id` 列表 |

**响应**：
- 200：`Result<List<BidWinningSupplierDetail>>`，`data` = 实际删除的实体列表

---

## 附录 A：业务码速查

| code | 触发条件 |
| --- | --- |
| 200 | 业务正常完成 |
| 400 | 参数校验失败 / 分页参数错误 |
| 404 | 按主键查询/更新/删除时记录不存在 |

## 附录 B：字段类型映射

| Java 类型 | JSON 类型 | 适用字段 |
| --- | --- | --- |
| String | string | 姓名、编号、文本 |
| Integer | number | 主键 ID、外键 ID |
| BigDecimal | number | 金额、成本、保证金 |
| Boolean | boolean | 是否固定价、是否需要征信 |
| LocalDateTime | string (ISO-8601) | 截止时间、创建时间 |
| List | array | 中标结果列表、批量删除 ID 列表 |

## 附录 C：与内部文档的索引

- 内部架构与扩展点：[architecture.md](architecture.md)
- 数据库表结构：[database.md](database.md)
- 端到端开发流程（含 7 层链路）：[development-flow.md](development-flow.md)

## 附录 D：更新日志

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-06-15 | v1.0 | 首次发布，覆盖 23 个端点（演示 3 + 邮件 2 + 经办人 6 + 定标通知 6 + 中标明细 6） |
