# API 文档（bid-winning-backend）

> 本文档描述了 `bid-winning-backend` 提供的全部对外 HTTP 接口。
>
> 内部架构、表结构、扩展开发流程等内容不在本文档范围，分别见 [architecture.md](architecture.md) / [database.md](database.md) / [development-flow.md](development-flow.md)。

---

## 0. 文档元信息

| 项 | 值                                                                                         |
| --- |-------------------------------------------------------------------------------------------|
| Base URL（dev） | `http://192.168.76.177:8080`                                                                   |
| Base URL（test） | `192.168.76.177:8080`                                                                     |
| Base URL（prod） | N/A                                                                                       |
| 请求体格式 | `application/json; charset=utf-8`（除 `/send-email` 使用 `application/x-www-form-urlencoded`） |
| 字符编码 | UTF-8                                                                                     |

### 0.1 接口速览

| 模块       | 端点数    | 说明                                                         |
|----------|--------|------------------------------------------------------------|
| 服务运行状态测试 | 3      | `GET /hello`、`GET /access-url`、`POST /fetch-url`           |
| 邮件发送     | 2      | `POST /send-email`（纯文本）、`POST /send-notification`（HTML 模板） |
| 经办人      | 6      | `bid_handler_info` 表 CRUD + 批量删除                           |
| 定标通知     | 6      | `bid_winning_notification` 表 CRUD + 批量删除                   |
| 中标结果明细   | 6      | `bid_winning_supplier_detail` 表 CRUD + 批量删除                |

---

## 1. 通用约定

### 1.1 统一响应结构（Result）

所有业务端点（演示/邮件除外）统一返回 `Result<T>` 包装结构Fastjson2 自动
序列化实体类为 JSON：


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
- 返回值必须是 `Result<T>` 类型（通过 `instanceof` 校验）

**邮件内容**（HTML 模板 `crud-result-notification.html`）：
- 操作类型徽章（CREATE / UPDATE / DELETE / BATCH_DELETE，颜色区分）
- 业务码（`Result.code`） + 消息（`Result.message`）
- 返回数据 JSON（`Result.data` 经 fastjson2 序列化）
- 接口路径（HTTP method + 类名#方法名）
- 时间戳

**注解参数**：
- `to`（必填）：收件人邮箱
- `subject`（可选）：自定义邮件主题，默认 `[业务操作通知] {endpoint} {operation} - {timestamp}`
- `templateName`（可选）：Thymeleaf 模板名，默认 `crud-result`

**时序**：
- Step 1：Controller 业务代码执行，return Result
- Step 2：触发 @AfterReturning 切面（主线程未放行）
  - Step 2.1：切面前半段（~微秒级，instanceof 验证 + 反射 + JSON 序列化 + 主题）
  - Step 2.2：`AsyncTaskManager.execute(...)` 提交异步任务，主线程仅 `new Thread + .start()`，**瞬间**完成
- Step 3：切面方法全部执行完毕（return）
- Step 4：Spring 代理对象放行，Spring MVC 序列化 Result 为 JSON，**HTTP 响应完成**
- Step 5（**与 Step 3-4 并行**）：后台子线程执行 sleep(0) + 邮件发送全流程

完整执行链路：见 [docs/architecture.md §7.4](architecture.md#74-完整业务执行生命周期5-步精确时序--主子线程分离)
AOP 切面开发模式：见 [docs/development-flow.md §8](development-flow.md#8-aop-切面开发模式4-步标准开发)

**收件人邮箱来源（优先级）**：

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1（最高） | HTTP Header `Target-Email-Address` | 调用方可在请求头中指定通知邮箱 |
| 2 | 注解 `to` 参数 | `@SendEmailOnSuccess(to = "ops@company.com")` |
| 3（最低） | 配置默认值 | 当前为 `ops@company.com`（占位符） |

**示例**（通过 header 指定通知邮箱）：
```bash
curl -X POST http://192.168.76.177:8080/create-bid-handler-info \
  -H "Content-Type: application/json" \
  -H "Target-Email-Address: admin@company.com" \
  -d '{"handlerName":"张三","companyName":"测试","departmentName":"采购部"}'
```

不传 `Target-Email-Address` header 时，使用注解 `to` 参数（`ops@company.com`）。

---

## 2. 服务状态接口（测试用）


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

**简述**：触发 3 秒延迟任务 + 立即返回`application.yml` 中 `custom.url` 配置的 URL

**请求**：无 body

**响应**：

```
Content-Type: text/plain;charset=UTF-8

https://baidu.com
```

调用 `HelloService.fetchAfterDelay()`，3 秒后在控制台打印"获取地址成功"。

---

## 3. 邮件接口（测试用）

### 3.1 `POST /send-email`

**简述**：发送纯文本邮件（同步）

**请求体**：URL-encoded（form）

**请求参数**（form 表单）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `to` | String | 是 | 收件人邮箱 |
| `subject` | String | 是 | 邮件主题 |
| `content` | String | 是 | 邮件正文（纯文本） |

> **注意**：`from` 字段由后端从 `spring.mail.username` 配置中读取（默认`1649681371@qq.com`），请求中传入的 `from` 被忽略。

**响应**：void（HTTP 200，body 为空）

**示例**：

```bash
curl -X POST http://192.168.76.177:8080/send-email \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "to=user@example.com&subject=测试&content=Hello"
```

---

### 3.2 `POST /send-notification`

**简述**：发送中标通知书 HTML 邮件（异步，延迟 5 秒）

**请求体**：JSON（raw）

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
curl -X POST http://192.168.76.177:8080/send-notification \
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

> 表单：`bid_handler_info` 
> 主键：`handler_id`（自增 INT）
> Controller：`BidHandlerInfoController`

> **本章所有端点均支持可选 Header `Target-Email-Address`**    **重要**：`Target-Email-Address` 是 **HTTP Header**（Postman 的 **Headers** 标签页），**不是 JSON body 字段**。用于覆盖通知邮件收件人。优先级：Header > 注解默认值 `ops@company.com`。详见 §1.7。

### 4.1 `POST /create-bid-handler-info`

**完整请求路径**：`http://192.168.76.177:8080/create-bid-handler-info`

**请求方式**：`POST`

**请求体**：JSON（raw）

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `handlerName` | Body | String | 是 | @NotBlank, @Size(max=50) | 经办人姓名 |
| `companyName` | Body | String | 是 | @NotBlank, @Size(max=100) | 所属采购公司 |
| `departmentName` | Body | String | 是 | @NotBlank, @Size(max=100) | 所属部门 |

> **注意**：`@Valid` 校验当前**未启用**（待 `GlobalExceptionHandler` 补全），字段缺失不会触发 400。

**调用链路**：Controller → Service → Mapper → MySQL INSERT（自增回填 handlerId）

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：`Result<BidHandlerInfo>`

**示例**：
```bash
curl -X POST http://192.168.76.177:8080/create-bid-handler-info \
  -H "Content-Type: application/json" \
  -d '{"handlerName":"张三","companyName":"测试公司","departmentName":"采购部"}'
```

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

**完整请求路径**：`http://192.168.76.177:8080/bid-handler-info/{id}`

**请求方式**：`GET`

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `id` | Path | Integer | 是 | 经办人主键 ID |

**调用链路**：Controller → Service → Mapper → MySQL SELECT（PK 索引）

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：
- 200：`Result<BidHandlerInfo>`，data = 实体
- 404：`Result<null>`，message = "记录不存在"

---

### 4.3 `PATCH /bid-handler-info/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-handler-info/{id}`

**请求方式**：`PATCH`

**请求体**：JSON（raw）

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `handlerName` | Body | String | 否 | @Size(max=50) | 省略/为 null = 不修改 |
| `companyName` | Body | String | 否 | @Size(max=100) | 同上 |
| `departmentName` | Body | String | 否 | @Size(max=100) | 同上 |

**调用链路**：Controller → Service.getById（取 before）→ 构造 after → Service.update → Mapper.updateById（动态 SQL，仅 SET 非 null 字段）

**技术栈**：Spring MVC / MyBatis-Plus（动态 SQL `<set><if>`）/ MySQL

**响应**：
- 200：`Result<Diff<BidHandlerInfo>>`，data = { before, after }
- 404：记录不存在

**示例**：
```bash
curl -X PATCH http://192.168.76.177:8080/bid-handler-info/1 \
  -H "Content-Type: application/json" \
  -d '{"handlerName":"李四"}'
```

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "before": { "handlerId": 1, "handlerName": "张三", "companyName": "原公司", "departmentName": "原部门" },
    "after":  { "handlerId": 1, "handlerName": "李四", "companyName": "原公司", "departmentName": "原部门" }
  }
}
```

---

### 4.4 `DELETE /bid-handler-info/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-handler-info/{id}`

**请求方式**：`DELETE`

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `id` | Path | Integer | 是 | 经办人主键 ID |

**调用链路**：Controller → Service → Mapper → MySQL DELETE

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：
- 200：`Result<null>`，message = "删除成功"
- 404：记录不存在

---

### 4.5 `GET /bid-handler-info`

**完整请求路径**：`http://192.168.76.177:8080/bid-handler-info?page=1&size=3&sort=handler_id%20desc`

**请求方式**：`GET`

**请求参数**：

| 参数 | 位置 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `page` | Query | Integer | 1 | 页码（1-based） |
| `size` | Query | Integer | 3 | 每页条数 |
| `sort` | Query | String | `handler_id desc` | 排序（PageHelper 原生格式，空格分隔） |

**调用链路**：Controller → PageHelper.startPage（ThreadLocal）→ Service.listAll → Mapper.selectAll（PageHelper 自动加 LIMIT + COUNT）→ PageInfo 包装 → finally clearPage

**技术栈**：Spring MVC / MyBatis-Plus / PageHelper / MySQL

**响应**：
- 200：`Result<PageInfo<BidHandlerInfo>>`
- 400：分页参数错误

**示例**：
```bash
curl "http://192.168.76.177:8080/bid-handler-info?page=1&size=3&sort=handler_id%20desc"
```

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

**完整请求路径**：`http://192.168.76.177:8080/bid-handler-info/batch-delete`

**请求方式**：`POST`

**请求体**：JSON（raw）

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `ids` | Body | Array\<Integer\> | 是 | @NotEmpty, @Size(max=500) | 待删除主键 ID 列表（1-500 条） |

**调用链路**：Controller → Service.selectByIds（查存在行）→ Service.deleteByIds（删存在行）→ 返实际删除的实体列表

**技术栈**：Spring MVC / MyBatis-Plus（`<foreach>` 拼接 IN）/ MySQL

**响应**：
- 200：`Result<List<BidHandlerInfo>>`，data = 实际删除的实体列表（DB 物理顺序）
- 400：@Valid 校验失败（待 GlobalExceptionHandler 启用）

**示例**：
```bash
curl -X POST http://192.168.76.177:8080/bid-handler-info/batch-delete \
  -H "Content-Type: application/json" \
  -d '{"ids":[1,2,3]}'
```

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

> 表单：`bid_winning_notification` 
> 主键：`notification_id`（自增 INT）
> Controller：`BidWinningNotificationController`

> **本章所有端点均支持可选 Header `Target-Email-Address`**    **重要**：`Target-Email-Address` 是 **HTTP Header**（Postman 的 **Headers** 标签页），**不是 JSON body 字段**。用于覆盖通知邮件收件人。优先级：Header > 注解默认值 `ops@company.com`。详见 §1.7。

### 5.1 `POST /create-bid-winning-notification`

**完整请求路径**：`http://192.168.76.177:8080/create-bid-winning-notification`

**请求方式**：`POST`

**请求体**：JSON（raw）

**请求参数**（`BidWinningNotificationCreateRequest`，17 字段）：

**必填字段（3 个）**：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `handlerId` | Body | Integer | 是 | @NotNull | 经办人 ID（关联 `bid_handler_info.handler_id`） |
| `projectNo` | Body | String | 是 | @NotBlank, @Size(max=50) | 项目编号 |
| `purchaseTaskNo` | Body | String | 是 | @NotBlank, @Size(max=50) | 采购任务编号 |

**可选字段（14 个）**：

| 参数 | 位置 | 类型 | 校验 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `platformCategory` | Body | String | @Size(max=50) | 平台采购类别 |
| `biddingProcess` | Body | String | @Size(max=50) | 招标流程 |
| `biddingMethod` | Body | String | @Size(max=50) | 招标方式 |
| `purchaseMethod` | Body | String | @Size(max=50) | 采购方式 |
| `isFixedPrice` | Body | Boolean | — | 是否固定总价 |
| `targetCost` | Body | BigDecimal | — | 目标成本（元） |
| `documentFee` | Body | BigDecimal | — | 标书费 |
| `deposit` | Body | BigDecimal | — | 保证金 |
| `creditReportRequired` | Body | Boolean | — | 是否需要征信报告 |
| `registrationDeadline` | Body | LocalDateTime | — | 报名截止时间 |
| `techBidDeadline` | Body | LocalDateTime | — | 技术回标截止时间 |
| `businessBidDeadline` | Body | LocalDateTime | — | 商务回标截止时间 |
| `biddingStatus` | Body | String | @Size(max=20) | 状态（DB 默认 "已完成"） |
| `notes` | Body | String | — | 备注 |

> **注意**：`createdAt` 由 DB 默认 `CURRENT_TIMESTAMP` 自动维护，请求中不传。

**调用链路**：Controller → Service → Mapper → MySQL INSERT（自增回填 notificationId + DB 默认 createdAt）

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：`Result<BidWinningNotification>`（含自增 `notificationId` 与 `createdAt`）

---

### 5.2 `GET /bid-winning-notification/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-notification/{id}`

**请求方式**：`GET`

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `id` | Path | Integer | 是 | 定标通知主键 ID |

**调用链路**：Controller → Service → Mapper → MySQL SELECT（PK 索引）

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：
- 200：`Result<BidWinningNotification>`
- 404：记录不存在

---

### 5.3 `PATCH /bid-winning-notification/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-notification/{id}`

**请求方式**：`PATCH`

**请求体**：JSON（raw）

**请求参数**（`BidWinningNotificationUpdateRequest`，17 字段全部可选）：

17 字段结构同 §5.1，**全部**仅含 `@Size` 校验（无 `@NotBlank` / `@NotNull`），PATCH 语义下 null = 不修改。

> **排除**：`notificationId`（由路径传入）、`createdAt`（DB 维护，结构上不可改）

**调用链路**：Controller → Service.getById（取 before）→ 构造 after → Service.update → Mapper.updateById（动态 SQL，仅 SET 非 null 字段）

**技术栈**：Spring MVC / MyBatis-Plus（动态 SQL `<set><if>`）/ MySQL

**响应**：
- 200：`Result<Diff<BidWinningNotification>>`
- 404：记录不存在

---

### 5.4 `DELETE /bid-winning-notification/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-notification/{id}`

**请求方式**：`DELETE`

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `id` | Path | Integer | 是 | 定标通知主键 ID |

**调用链路**：Controller → Service → Mapper → MySQL DELETE

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：
- 200：`Result<null>`，message = "删除成功"
- 404：记录不存在

---

### 5.5 `GET /bid-winning-notification`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-notification?pageNum=1&pageSize=10&sort=notification_id%20desc`

**请求方式**：`GET`

**请求参数**：

| 参数 | 位置 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `pageNum` | Query | Integer | 1 | 页码 |
| `pageSize` | Query | Integer | 10 | 每页条数 |
| `sort` | Query | String | `notification_id desc` | 排序 |

**调用链路**：Controller → PageHelper.startPage → Service.listAll → Mapper.selectAll（PageHelper 加 LIMIT + COUNT）→ PageInfo 包装 → finally clearPage

**技术栈**：Spring MVC / MyBatis-Plus / PageHelper / MySQL

**响应**：
- 200：`Result<PageInfo<BidWinningNotification>>`
- 400：分页参数错误

---

### 5.6 `POST /bid-winning-notification/batch-delete`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-notification/batch-delete`

**请求方式**：`POST`

**请求体**：JSON（raw）

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `ids` | Body | Array\<Integer\> | 是 | @NotEmpty, @Size(max=500) | 待删除 `notification_id` 列表 |

**调用链路**：Controller → Service.selectByIds（查存在行）→ Service.deleteByIds（删存在行）→ 返实际删除的实体列表

**技术栈**：Spring MVC / MyBatis-Plus（`<foreach>` 拼接 IN）/ MySQL

**响应**：
- 200：`Result<List<BidWinningNotification>>`，data = 实际删除的实体列表

> **逻辑外键警告**：`bid_winning_supplier_detail.notification_id` 是逻辑外键（DB 无 FK 约束）。删除本表后，明细行会变孤儿，应用层需自行处理。

---

## 6. 中标结果明细（bid_winning_supplier_detail）

> 表单：`bid_winning_supplier_detail` 表
> 主键：`detail_id`（自增 INT）
> Controller：`BidWinningSupplierDetailController`

> **本章所有端点均支持可选 Header `Target-Email-Address`**    **重要**：`Target-Email-Address` 是 **HTTP Header**（Postman 的 **Headers** 标签页），**不是 JSON body 字段**。用于覆盖通知邮件收件人。优先级：Header > 注解默认值 `ops@company.com`。详见 §1.7。

### 6.1 `POST /create-bid-winning-supplier-detail`

**完整请求路径**：`http://192.168.76.177:8080/create-bid-winning-supplier-detail`

**请求方式**：`POST`

**请求体**：JSON（raw）

**请求参数**（`BidWinningSupplierDetailCreateRequest`，3 字段）：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `notificationId` | Body | Integer | 是 | @NotNull | 定标通知 ID（关联 `bid_winning_notification.notification_id`） |
| `supplierName` | Body | String | 是 | @NotBlank, @Size(max=100) | 中标供应商名称 |
| `amount` | Body | BigDecimal | 是 | @NotNull | 中标价（元） |

**调用链路**：Controller → Service → Mapper → MySQL INSERT（自增回填 detailId）

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：`Result<BidWinningSupplierDetail>`（含自增 `detailId`）

---

### 6.2 `GET /bid-winning-supplier-detail/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-supplier-detail/{id}`

**请求方式**：`GET`

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `id` | Path | Integer | 是 | 明细主键 ID |

**调用链路**：Controller → Service → Mapper → MySQL SELECT（PK 索引）

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：
- 200：`Result<BidWinningSupplierDetail>`
- 404：记录不存在

---

### 6.3 `PATCH /bid-winning-supplier-detail/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-supplier-detail/{id}`

**请求方式**：`PATCH`

**请求体**：JSON（raw）

**请求参数**（`BidWinningSupplierDetailUpdateRequest`，3 字段全部可选）：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `notificationId` | Body | Integer | 否 | — | 省略/为 null = 不修改 |
| `supplierName` | Body | String | 否 | @Size(max=100) | 同上 |
| `amount` | Body | BigDecimal | 否 | — | 同上 |

**调用链路**：Controller → Service.getById（取 before）→ 构造 after → Service.update → Mapper.updateById（动态 SQL，仅 SET 非 null 字段）

**技术栈**：Spring MVC / MyBatis-Plus（动态 SQL `<set><if>`）/ MySQL

**响应**：
- 200：`Result<Diff<BidWinningSupplierDetail>>`
- 404：记录不存在

---

### 6.4 `DELETE /bid-winning-supplier-detail/{id}`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-supplier-detail/{id}`

**请求方式**：`DELETE`

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `id` | Path | Integer | 是 | 明细主键 ID |

**调用链路**：Controller → Service → Mapper → MySQL DELETE

**技术栈**：Spring MVC / MyBatis-Plus / MySQL

**响应**：
- 200：`Result<null>`，message = "删除成功"
- 404：记录不存在

---

### 6.5 `GET /bid-winning-supplier-detail`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-supplier-detail?pageNum=1&pageSize=10&sort=detail_id%20desc`

**请求方式**：`GET`

**请求参数**：

| 参数 | 位置 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`）|
| `pageNum` | Query | Integer | 1 | 页码 |
| `pageSize` | Query | Integer | 10 | 每页条数 |
| `sort` | Query | String | `detail_id desc` | 排序 |

**调用链路**：Controller → PageHelper.startPage → Service.listAll → Mapper.selectAll（PageHelper 加 LIMIT + COUNT）→ PageInfo 包装 → finally clearPage

**技术栈**：Spring MVC / MyBatis-Plus / PageHelper / MySQL

**响应**：
- 200：`Result<PageInfo<BidWinningSupplierDetail>>`
- 400：分页参数错误

---

### 6.6 `POST /bid-winning-supplier-detail/batch-delete`

**完整请求路径**：`http://192.168.76.177:8080/bid-winning-supplier-detail/batch-delete`

**请求方式**：`POST`

**请求体**：JSON（raw）

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `Target-Email-Address` | **HTTP Header**   | String | 否 | — |   **Header 字段**（非 JSON body）— 通知邮件收件人（覆盖默认 `ops@company.com`），不传则用注解 to |
| `ids` | Body | Array\<Integer\> | 是 | @NotEmpty, @Size(max=500) | 待删除 `detail_id` 列表 |

**调用链路**：Controller → Service.selectByIds（查存在行）→ Service.deleteByIds（删存在行）→ 返实际删除的实体列表

**技术栈**：Spring MVC / MyBatis-Plus（`<foreach>` 拼接 IN）/ MySQL

**响应**：
- 200：`Result<List<BidWinningSupplierDetail>>`，data = 实际删除的实体列表

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

- 内部架构：[architecture.md](architecture.md)
- 数据库表结构：[database.md](database.md)
- 开发流程（含 7 层链路）：[development-flow.md](development-flow.md)


