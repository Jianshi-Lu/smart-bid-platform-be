# Create 接口规范（3 个 Controller × 1 create 端点）

> 本文档明确 3 个 Controller 下 create 端点的请求/响应规范，对应 3 张不同业务表。
> 文档元信息：Base URL（dev）`http://192.168.76.177:8080`，请求体格式 `application/json; charset=utf-8`，字符编码 `UTF-8`。
> 统一响应包装：`Result<T>`（`code` 业务状态码 + `message` 文字消息 + `data` 业务数据载荷）。
> 业务状态码约定：200=成功，400=参数错误，404=资源不存在。

---

# 接口明细 1 经办人 `bid_handler_info`

> 数据库表：`bid_handler_info`
> 主键：`handler_id`（自增 INT）

## 接口明细 1.1.1 创建经办人

**接口概述**：本接口适用于新建一条经办人记录，姓名 / 公司 / 部门均必填。创建成功后通过 AOP 切面（`CrudResultEmailAspect`）异步发送邮件通知。


**请求方式**：POST

**api地址**：`http://192.168.76.177:8080/create-bid-handler-info`

**输入项**（`BidHandlerInfoCreateRequest`，3 字段全必填）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | handlerName | String | 经办人姓名 | @NotBlank，@Size(max=50) |
| 2 | 是 | companyName | String | 所属采购公司 | @NotBlank，@Size(max=100) |
| 3 | 是 | departmentName | String | 所属部门 | @NotBlank，@Size(max=100) |

**输出项**（统一 `Result<T>` 包装）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | code | Integer | 业务状态码 | 200=成功；400=参数错误；404=资源不存在 |
| 2 | 是 | message | String | 业务消息 | 示例："创建成功" |
| 3 | 是 | data | Object | 新创建的经办人实体 | 见下方「经办人实体类」 |

**经办人实体类**（`data` 字段结构，`BidHandlerInfo`，4 字段）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | handlerId | Integer | 主键 | DB 自增 |
| 2 | 是 | handlerName | String | 经办人姓名 | |
| 3 | 是 | companyName | String | 所属采购公司 | |
| 4 | 是 | departmentName | String | 所属部门 | |

**请求报文**：JSON
```json
{
  "handlerName": "张三",
  "companyName": "测试采购有限公司",
  "departmentName": "采购部"
}
```

**响应报文**：JSON
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "handlerId": 1,
    "handlerName": "张三",
    "companyName": "测试采购有限公司",
    "departmentName": "采购部"
  }
}
```

**附注（可选 Header）**：
- `Target-Email-Address`（HTTP Header，**非 JSON body 字段**）— 通知邮件收件人；不传则用 `@SendEmailOnSuccess` 注解默认值 `ops@company.com`
- 当前 `@Valid` 校验未启用，必填字段缺失不会触发 400（待 `GlobalExceptionHandler` 补全）

---

# 接口明细 2 定标通知 `bid_winning_notification`

> 数据库表：`bid_winning_notification`
> 主键：`notification_id`（自增 INT）

## 接口明细 2.1.1 创建定标通知

**接口概述**：本接口适用于新建一条定标通知记录。17 字段中 3 字段必填（经办人 ID、项目编号、采购任务编号），其余 14 字段可选。创建成功后服务端自动写入 `createdAt` 当前时间，并触发 AOP 邮件通知。

**请求方式**：POST

**api地址**：`http://192.168.76.177:8080/create-bid-winning-notification`

**输入项**（`BidWinningNotificationCreateRequest`，17 字段 = 3 必填 + 14 可选）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | handlerId | Integer | 经办人 ID | @NotNull，逻辑外键 → `bid_handler_info.handler_id` |
| 2 | 是 | projectNo | String | 项目编号 | @NotBlank，@Size(max=50) |
| 3 | 是 | purchaseTaskNo | String | 采购任务编号 | @NotBlank，@Size(max=50) |
| 4 | 否 | platformCategory | String | 平台采购类别 | @Size(max=50) |
| 5 | 否 | biddingProcess | String | 招标流程 | @Size(max=50) |
| 6 | 否 | biddingMethod | String | 招标方式 | @Size(max=50) |
| 7 | 否 | purchaseMethod | String | 采购方式 | @Size(max=50) |
| 8 | 否 | isFixedPrice | Boolean | 是否固定价 | |
| 9 | 否 | targetCost | BigDecimal | 目标成本（元） | |
| 10 | 否 | documentFee | BigDecimal | 文件费（元） | |
| 11 | 否 | deposit | BigDecimal | 保证金（元） | |
| 12 | 否 | creditReportRequired | Boolean | 是否需要资信报告 | |
| 13 | 否 | registrationDeadline | LocalDateTime | 注册截止时间 | 格式 `yyyy-MM-ddTHH:mm:ss` |
| 14 | 否 | techBidDeadline | LocalDateTime | 技术标截止时间 | 格式 `yyyy-MM-ddTHH:mm:ss` |
| 15 | 否 | businessBidDeadline | LocalDateTime | 商务标截止时间 | 格式 `yyyy-MM-ddTHH:mm:ss` |
| 16 | 否 | biddingStatus | String | 招标状态 | @Size(max=20)，DB 默认 "已完成" |
| 17 | 否 | notes | String | 备注 | TEXT |

**输出项**（统一 `Result<T>` 包装）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | code | Integer | 业务状态码 | 200=成功；400=参数错误 |
| 2 | 是 | message | String | 业务消息 | 示例："创建成功" |
| 3 | 是 | data | Object | 新创建的定标通知实体 | 见下方「定标通知实体类」 |

**定标通知实体类**（`data` 字段结构，`BidWinningNotification`，19 字段 = 17 输入 + 2 系统）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | notificationId | Integer | 主键 | DB 自增 |
| 2 | 是 | handlerId | Integer | 经办人 ID | |
| 3 | 是 | projectNo | String | 项目编号 | |
| 4 | 是 | purchaseTaskNo | String | 采购任务编号 | |
| 5 | 是 | platformCategory | String | 平台采购类别 | |
| 6 | 是 | biddingProcess | String | 招标流程 | |
| 7 | 是 | biddingMethod | String | 招标方式 | |
| 8 | 是 | purchaseMethod | String | 采购方式 | |
| 9 | 是 | isFixedPrice | Boolean | 是否固定价 | |
| 10 | 是 | targetCost | BigDecimal | 目标成本（元） | |
| 11 | 是 | documentFee | BigDecimal | 文件费（元） | |
| 12 | 是 | deposit | BigDecimal | 保证金（元） | |
| 13 | 是 | creditReportRequired | Boolean | 是否需要资信报告 | |
| 14 | 是 | registrationDeadline | LocalDateTime | 注册截止时间 | |
| 15 | 是 | techBidDeadline | LocalDateTime | 技术标截止时间 | |
| 16 | 是 | businessBidDeadline | LocalDateTime | 商务标截止时间 | |
| 17 | 是 | biddingStatus | String | 招标状态 | |
| 18 | 是 | notes | String | 备注 | |
| 19 | 是 | createdAt | LocalDateTime | 创建时间 | **服务端自动写入**，不可由请求覆盖 |

**请求报文**：JSON
```json
{
  "handlerId": 1,
  "projectNo": "PRJ-2024-001",
  "purchaseTaskNo": "PT-2024-001",
  "platformCategory": "工程类",
  "biddingProcess": "公开招标",
  "biddingMethod": "公开",
  "purchaseMethod": "询价",
  "isFixedPrice": true,
  "targetCost": 1000000.00,
  "documentFee": 500.00,
  "deposit": 50000.00,
  "creditReportRequired": true,
  "registrationDeadline": "2024-12-31T17:00:00",
  "techBidDeadline": "2025-01-15T17:00:00",
  "businessBidDeadline": "2025-01-20T17:00:00",
  "biddingStatus": "进行中",
  "notes": "本年度重点采购项目"
}
```

**响应报文**：JSON
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "notificationId": 1,
    "handlerId": 1,
    "projectNo": "PRJ-2024-001",
    "purchaseTaskNo": "PT-2024-001",
    "platformCategory": "工程类",
    "biddingProcess": "公开招标",
    "biddingMethod": "公开",
    "purchaseMethod": "询价",
    "isFixedPrice": true,
    "targetCost": 1000000.00,
    "documentFee": 500.00,
    "deposit": 50000.00,
    "creditReportRequired": true,
    "registrationDeadline": "2024-12-31T17:00:00",
    "techBidDeadline": "2025-01-15T17:00:00",
    "businessBidDeadline": "2025-01-20T17:00:00",
    "biddingStatus": "进行中",
    "notes": "本年度重点采购项目",
    "createdAt": "2024-12-15T10:30:00"
  }
}
```

**附注（可选 Header）**：
- `Target-Email-Address`（HTTP Header，**非 JSON body 字段**）— 通知邮件收件人；不传则用 `@SendEmailOnSuccess` 注解默认值 `ops@company.com`
- 当前 `@Valid` 校验未启用，必填字段缺失不会触发 400（待 `GlobalExceptionHandler` 补全）

---

# 接口明细 3 中标结果明细 `bid_winning_supplier_detail`

> 数据库表：`bid_winning_supplier_detail`
> 主键：`detail_id`（自增 INT）

## 接口明细 3.1.1 创建中标结果明细

**接口概述**：本接口适用于为某条定标通知追加一条中标供应商明细。3 字段全部必填，其中 `notificationId` 是逻辑外键（应用层维护，DB 无 FK 约束）。创建成功后触发 AOP 邮件通知。

**请求方式**：POST

**api地址**：`http://192.168.76.177:8080/create-bid-winning-supplier-detail`

**输入项**（`BidWinningSupplierDetailCreateRequest`，3 字段全必填）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | notificationId | Integer | 所属定标通知 ID | @NotNull，逻辑外键 → `bid_winning_notification.notification_id` |
| 2 | 是 | supplierName | String | 中标供应商名称 | @NotBlank，@Size(max=100) |
| 3 | 是 | amount | BigDecimal | 中标金额（元） | @NotNull |

**输出项**（统一 `Result<T>` 包装）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | code | Integer | 业务状态码 | 200=成功；400=参数错误 |
| 2 | 是 | message | String | 业务消息 | 示例："创建成功" |
| 3 | 是 | data | Object | 新创建的中标明细实体 | 见下方「中标结果明细实体类」 |

**中标结果明细实体类**（`data` 字段结构，`BidWinningSupplierDetail`，4 字段 = 3 输入 + 1 主键）：

| 序号 | 必接项 | 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|---|---|
| 1 | 是 | detailId | Integer | 主键 | DB 自增 |
| 2 | 是 | notificationId | Integer | 所属定标通知 ID | |
| 3 | 是 | supplierName | String | 中标供应商名称 | |
| 4 | 是 | amount | BigDecimal | 中标金额（元） | |

**请求报文**：JSON
```json
{
  "notificationId": 1,
  "supplierName": "ABC 建筑工程有限公司",
  "amount": 1234567.89
}
```

**响应报文**：JSON
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "detailId": 1,
    "notificationId": 1,
    "supplierName": "ABC 建筑工程有限公司",
    "amount": 1234567.89
  }
}
```

**附注（可选 Header）**：
- `Target-Email-Address`（HTTP Header，**非 JSON body 字段**）— 通知邮件收件人；不传则用 `@SendEmailOnSuccess` 注解默认值 `ops@company.com`
- **逻辑外键警告**：`notificationId` 需调用方保证指向已存在的 `bid_winning_notification.notification_id`，DB 层不强制；引用不存在的 ID 不会触发错误，但会形成孤儿数据
- 当前 `@Valid` 校验未启用，必填字段缺失不会触发 400（待 `GlobalExceptionHandler` 补全）
