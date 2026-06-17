# AGENTS.md

> bid-winning-backend 项目的 Agent 工作约定。详细架构/API/表结构/开发流程见 `docs/` 下各 MD。

## Build & Run

- 用 `./mvnw`（Maven wrapper 已包含），不依赖系统 Maven
- 运行：`./mvnw spring-boot:run`
- 测试：`./mvnw test`
- 打包：`./mvnw clean package`

## Tech Stack

- **Java 25**（必需）
- **Spring Boot 4.0.6** + `spring-boot-starter-webmvc`（Servlet-based，非 Reactive）
- **MyBatis-Plus 3.5.15** + `mybatis-plus-spring-boot4-starter`
- **PageHelper 4.1.0** + `pagehelper-spring-boot-starter`（分页）
- **Lombok 1.18.46**（`@Data` / `@NoArgsConstructor` / `@AllArgsConstructor`）
- **Thymeleaf 3.1.5**（邮件 HTML 模板）
- **Spring Boot Starter Mail**（`JavaMailSender`）
- **AspectJ 1.9.25.1**（AOP 邮件通知，注意：Spring Boot 4 已移除 `spring-boot-starter-aop`，需手动引入 `org.aspectj:aspectjweaver`）
- **DevTools**（运行时重载）

## Project Structure

> 源根包：`io.gitee.lujianshi.bidwinningbackend`
> 单模块 Maven 项目，标准 `src/main/java` + `src/main/resources` + `src/test/java` 布局

```
src/main/java/io/gitee/lujianshi/bidwinningbackend/
├── BidWinningBackendApplication.java       # Spring Boot 启动入口
├── annotation/
│   └── SendEmailOnSuccess.java             # @Target(METHOD) + @Retention(RUNTIME) 邮件通知标记
├── aspect/
│   └── CrudResultEmailAspect.java          # @AfterReturning 拦截 @SendEmailOnSuccess
├── controller/
│   ├── bidhandlerinfo/
│   │   └── BidHandlerInfoController.java   # 经办人 CRUD（6 端点）
│   ├── bidwinningnotification/
│   │   └── BidWinningNotificationController.java   # 定标通知 CRUD（6 端点）
│   ├── bidwinningsupplierdetail/
│   │   └── BidWinningSupplierDetailController.java # 中标结果明细 CRUD（6 端点）
│   └── statuscheck/
│       └── StatusCheckController.java      # 服务状态 + 邮件测试（3 端点）
├── dto/
│   ├── common/
│   │   ├── Result.java                     # 统一响应包装（code/message/data + success/error 工厂）
│   │   └── Diff.java                       # PATCH before/after diff 包装
│   ├── bidhandlerinfo/                     # 经办人 3 DTO（Create/Update/BatchDelete）
│   ├── bidwinningnotification/             # 定标通知 3 DTO
│   ├── bidwinningsupplierdetail/           # 中标明细 3 DTO
│   └── mail/
│       ├── PlainTextMailRequest.java       # 纯文本邮件（to/from/subject/content）
│       ├── BidWinningMailRequest.java      # extends PlainTextMailRequest + projectCode/handlerName/bidWinningList/templateName
│       └── BidWinningListItem.java         # 中标结果条目（supplierName/bidWinningAmount）
├── entity/
│   ├── BidHandlerInfo.java                 # 经办人实体
│   ├── BidWinningNotification.java         # 定标通知实体（19 字段）
│   └── BidWinningSupplierDetail.java       # 中标明细实体
├── mapper/
│   ├── BidHandlerInfoMapper.java           # @Mapper 接口（不继承 BaseMapper）
│   ├── BidWinningNotificationMapper.java   # @Mapper 接口
│   └── BidWinningSupplierDetailMapper.java # @Mapper 接口
├── service/
│   ├── bidhandlerinfo/BidHandlerInfoService.java      # @Service
│   ├── bidwinningnotification/BidWinningNotificationService.java
│   ├── bidwinningsupplierdetail/BidWinningSupplierDetailService.java
│   └── mail/
│       ├── MailService.java                # 邮件发送（sendEmail 纯文本 + sendHtmlEmail HTML）
│       ├── TemplateFiller.java             # Thymeleaf 模板渲染
│       └── ResourceSearcher.java           # classpath 模板文件存在性校验
└── utils/
    └── AsyncTaskManager.java               # 延迟异步任务执行器（纯 Java 线程，非 Spring Bean）

src/main/resources/
├── mapper/                                 # MyBatis XML（namespace = Mapper 接口 FQN）
│   ├── BidHandlerInfoMapper.xml
│   ├── BidWinningNotificationMapper.xml
│   └── BidWinningSupplierDetailMapper.xml
├── sql/ddl/                                # Flyway 兼容命名的 DDL 脚本（V1-V5，**未启用 Flyway**）
│   ├── V1__create_handler_info.sql
│   ├── V2__update_handler_info_structure.sql
│   ├── V3__update_handler_info_to_bid_handler_info.sql
│   ├── V4__create_bid_winning_notification.sql
│   └── V5__create_bid_winning_supplier_detail.sql
├── templates/                              # Thymeleaf 邮件模板
│   ├── bid-winning-notification.html       # 中标通知书模板
│   ├── bid-winning-notification2.html      # 中标通知书模板变体
│   └── crud-result-notification.html       # AOP CRUD 结果通知模板
├── application.yml                         # 公共配置（应用名 + Thymeleaf 路径）
├── application-dev.yml                     # 开发环境（默认，端口 8080）
├── application-test.yml                    # 测试环境（端口 8080，DEBUG 日志）
└── application-prod.yml                    # 生产环境（端口 80，敏感信息走环境变量）
```

## Entry Point

- `BidWinningBackendApplication.java` — Spring Boot 启动入口（纯 bootstrap，无路由）
  - 启用注解：`@SpringBootApplication` + `@EnableAsync` + `@EnableAspectJAutoProxy` + `@MapperScan`
- Dev server: `http://localhost:8080`
- 配置端口: `server.port=8080`（application-dev.yml）

## Profiles（多环境配置）

| Profile | 端口 | MySQL | 邮件 | 用途 |
|---------|------|-------|------|------|
| `dev`（默认）| 8080 | `${MYSQL_URL/USER/PASS}` 占位符 | 明文密码 | 本地开发 |
| `test` | 8080 | `${MYSQL_URL/USER/PASS}` 占位符 | 明文密码 | 集成测试 |
| `prod` | 80 | 环境变量 | `${PROD_MAIL_*}` 强制 | 生产部署 |

**切换方式**：
1. 修改 `application.yml` 中 `spring.profiles.active`
2. 启动命令：`./mvnw spring-boot:run -Dspring-boot.run.profiles=test`
3. 环境变量：`export SPRING_PROFILES_ACTIVE=prod`

## 环境变量配置

### 占位符变量（启动时必须提供）

| 变量 | 必填 | 用途 | 示例 |
|------|:----:|------|------|
| `MYSQL_URL` | ✅ dev/test | JDBC URL | `jdbc:mysql://10.0.0.100:3306/bid-winning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` |
| `MYSQL_USERNAME` | ✅ dev/test | DB 用户名 | `jianshi` |
| `MYSQL_PASSWORD` | ✅ dev/test | DB 密码 | `Ljs83455012.` |
| `PROD_MAIL_USERNAME` | ⚠️ prod | 发件邮箱 | `service@company.com` |
| `PROD_MAIL_PASSWORD` | ⚠️ prod | 邮箱授权码 | `xxxx` |
| `PROD_MAIL_HOST` | ❌ 默认 `smtp.qq.com` | SMTP 主机 | `smtp.exmail.qq.com` |
| `PROD_MAIL_PORT` | ❌ 默认 `587` | SMTP 端口 | `465` |

> ⚠️ dev/test 必须设 `MYSQL_*` 三个变量；prod 必须设 `PROD_MAIL_USERNAME` + `PROD_MAIL_PASSWORD`。
> dev 邮件密码明文配置（开发效率优先），prod 强制走环境变量。

### dev 环境提供方式

1. **IDEA Run/Debug Configurations** → Environment variables
2. **Shell 启动**：
   ```bash
   export MYSQL_URL='jdbc:mysql://...' MYSQL_USERNAME=jianshi MYSQL_PASSWORD=xxx
   ./mvnw spring-boot:run
   ```
3. **JVM 系统属性**（最稳）：
   ```
   -DMYSQL_URL=jdbc:mysql://... -DMYSQL_USERNAME=jianshi -DMYSQL_PASSWORD=xxx
   ```

### 常见错误

| 错误 | 现象 | 解决 |
|------|------|------|
| 变量名拼写错误 | `Access denied for user '${MYSQL_USERNAME}'` | 检查环境变量名拼写 |
| 多个 Run Configuration 混淆 | 旧配置仍生效 | 删除或修正 Run Configuration |
| 没点 Apply / OK | 环境变量未保存 | 改完配置点 Apply 再 OK |
| dev 切到 prod | 缺 `${PROD_MAIL_*}` 占位符报错 | 加环境变量或切回 dev profile |
| 缺 `allowPublicKeyRetrieval=true` | MySQL 8 + SSL 关闭时 `Public Key Retrieval is not allowed` | URL 末尾加 `&allowPublicKeyRetrieval=true` |

## API Endpoints（21 端点）

> 完整规范（请求/响应字段表 + curl 示例）见 `docs/api.md`。
> 业务 Controller 详细规范见 `docs/business-controllers.md`。
> 邮件通知 AOP 详细执行链路见 `docs/aop-execution-chain.md`。

### 服务状态 + 邮件测试（StatusCheckController，3 端点）

| Method | Path | Content-Type | Description |
|-------|------|-------------|-------------|
| GET | `/service-status` | - | 返回 "Service is up and running."（Spring 服务存活探活） |
| POST | `/send-email` | `application/json` | 发送纯文本邮件（`MailService.sendEmail`） |
| POST | `/send-notification` | `application/json` | 发送中标通知书 HTML 邮件（模板渲染 + 异步延迟5秒） |

### 业务 CRUD（3 Controller × 6 端点 = 18 端点）

> 所有业务端点：
> - 统一返回 `Result<T>` 包装
> - 均带 `@SendEmailOnSuccess(to = "ops@company.com")` 注解（AOP 拦截异步发邮件）
> - 收件人优先级：`Target-Email-Address` HTTP Header > 注解 `to` > 默认 `ops@company.com`

**经办人（bid_handler_info）— `BidHandlerInfoController`**

| Method | Path | Content-Type | Response data |
|-------|------|-------------|---------------|
| POST | `/create-bid-handler-info` | `application/json` | `BidHandlerInfo` |
| GET | `/bid-handler-info/{id}` | - | `BidHandlerInfo`（404→null） |
| PATCH | `/bid-handler-info/{id}` | `application/json` | `Diff<BidHandlerInfo>` |
| DELETE | `/bid-handler-info/{id}` | - | `null` |
| GET | `/bid-handler-info` | - | `PageInfo<BidHandlerInfo>`（query: page/size/sort） |
| POST | `/bid-handler-info/batch-delete` | `application/json` | `List<BidHandlerInfo>`（p3 升级版，data=实际删除的实体列表） |

**定标通知（bid_winning_notification）— `BidWinningNotificationController`**

| Method | Path | Content-Type | Response data |
|-------|------|-------------|---------------|
| POST | `/create-bid-winning-notification` | `application/json` | `BidWinningNotification` |
| GET | `/bid-winning-notification/{id}` | - | `BidWinningNotification` |
| PATCH | `/bid-winning-notification/{id}` | `application/json` | `Diff<BidWinningNotification>` |
| DELETE | `/bid-winning-notification/{id}` | - | `null` |
| GET | `/bid-winning-notification` | - | `PageInfo<BidWinningNotification>` |
| POST | `/bid-winning-notification/batch-delete` | `application/json` | `List<BidWinningNotification>` |

**中标结果明细（bid_winning_supplier_detail）— `BidWinningSupplierDetailController`**

| Method | Path | Content-Type | Response data |
|-------|------|-------------|---------------|
| POST | `/create-bid-winning-supplier-detail` | `application/json` | `BidWinningSupplierDetail` |
| GET | `/bid-winning-supplier-detail/{id}` | - | `BidWinningSupplierDetail` |
| PATCH | `/bid-winning-supplier-detail/{id}` | `application/json` | `Diff<BidWinningSupplierDetail>` |
| DELETE | `/bid-winning-supplier-detail/{id}` | - | `null` |
| GET | `/bid-winning-supplier-detail` | - | `PageInfo<BidWinningSupplierDetail>` |
| POST | `/bid-winning-supplier-detail/batch-delete` | `application/json` | `List<BidWinningSupplierDetail>` |

## AOP 邮件通知（`@SendEmailOnSuccess` + `CrudResultEmailAspect`）

> 完整执行链路（5 步主线程 + 4 步异步子线程）见 `docs/aop-execution-chain.md`

**触发条件**：`@AfterReturning` 拦截带 `@SendEmailOnSuccess` 注解的方法，且返回 `Result` 且 `code == 200`

**收件人解析优先级**：
1. HTTP Header `Target-Email-Address`（非空时优先）
2. 注解 `to()` 默认值
3. 兜底 `ops@company.com`（注解 `to` 未设置时）

**通知模板**：`templates/crud-result-notification.html`（`op-CREATE/UPDATE/DELETE/BATCH_DELETE` 颜色编码）

**关键点**：
- 异步执行：主线程不阻塞（微秒级）；子线程用 `AsyncTaskManager`（纯 Java `new Thread().start()`，非 Spring `@Async` 代理）
- 主题格式：`[{op}] {endpoint 摘要}`（如 `[CREATE] /create-bid-handler-info`）
- 响应 `data` 通过 fastjson2 序列化为 JSON 嵌入邮件

## Async Task（`AsyncTaskManager`）

- 静态方法 `execute(Runnable task, long delayMillis)`
- 纯 Java 线程，非 Spring Bean（每次 `new Thread().start()`）
- 使用场景：
  - `/send-notification` 延迟 5 秒发邮件（让前端展示"发送中"状态）
  - AOP 切面异步发邮件（0ms 延迟）
- 优势：无需 `@Async` + Spring AOP 代理，支持任意延迟
- 注意：高频调用建议改用线程池（当前每次 `new Thread` 有创建开销）

## 关键设计约定

| 维度 | 约定 |
|------|------|
| Mapper 接口 | 不继承 `BaseMapper<T>`，手写方法签名 + XML SQL |
| Service | 不继承 `IService<T>`，不继承 `ServiceImpl`，直接注入 Mapper |
| 写方法事务 | `@Transactional(rollbackFor = Exception.class)` |
| DTO 命名 | `<Entity>CreateRequest` / `<Entity>UpdateRequest`（PATCH 风格，**仅 `@Size`，无 `@NotBlank`/`@NotNull`**）/ `<Entity>BatchDeleteRequest`（独立，不复用） |
| Controller 响应 | `Result<T>` 包装；PATCH → `Result<Diff<T>>`；批量删除 → `Result<List<Entity>>`（p3 升级版） |
| 业务状态码 | 200=成功, 400=参数错误, 404=资源不存在 |
| 注入方式 | `jakarta.annotation.Resource`（field 注入） |
| `@Valid` | 当前**未**在 Controller 加 `@Valid`，DTO 校验注解暂未生效（待 `GlobalExceptionHandler` 补全） |
| 外键 | `handler_id` / `notification_id` 为**逻辑外键**，DB 层不强制 FK 约束 |
| Flyway | **未**启用，仅借用 V*.sql 命名 |
| JSON 序列化 | AOP 用 fastjson2（项目无 Jackson） |
| Spring Boot 4 注意 | **无** `spring-boot-starter-aop` artifact，需用 `org.aspectj:aspectjweaver` |
| Bean 名冲突 | `JavaMailSender` Bean 名 `mailSender`，自建类**不可**叫 `MailSender`（会冲突） |
| `Page<Object>` | 不实现 `AutoCloseable`，**不可**用 try-with-resources |

## 端到端开发流程（7 层链路）

> 详见 `docs/development-flow.md`（以 BidHandlerInfo 为完整示例）

```
① DB 脚本（V*.sql，Flyway 兼容命名，当前未启用 Flyway）
  ↓
② Entity（@TableName + @TableId）
  ↓
③ Mapper 接口（@Mapper 注解 + 手写方法签名）
  ↓
④ Mapper XML（namespace 绑定接口，SQL 独立于 Java）
  ↓
⑤ Service（注入 Mapper，不继承 IService）
  ↓
⑥ DTO（@NotBlank / @Size 校验）
  ↓
⑦ Controller（@RestController + 路由注解，理想情况加 @Valid 触发校验）
```

## Testing

- `@SpringBootTest` for integration tests
- 默认测试：验证 Spring context 加载

## Lombok

- 注解处理器在 `maven-compiler-plugin` 中启用
- IDE 需安装 Lombok 插件才能解析编译时注解
- 常用注解：`@Data`（getter/setter/equals/hashCode/toString）/ `@NoArgsConstructor` / `@AllArgsConstructor`
