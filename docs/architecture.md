# 项目架构（bid-winning-backend）

> 项目内部架构文档。API 契约见 [`docs/api.md`](api.md)；数据库结构见 [`docs/database.md`](database.md)。

## 1. 技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 运行时 | Java | 25 |
| Web 框架 | Spring Boot | 4.0.6（`spring-boot-starter-webmvc`，Servlet-based） |
| ORM | MyBatis-Plus | 3.5.15（`mybatis-plus-spring-boot4-starter`） |
| 分页 | PageHelper | 4.1.0（`pagehelper-spring-boot-starter`） |
| 邮件 | Spring Boot Starter Mail | — |
| 模板引擎 | Thymeleaf | 3.1.5 |
| AI | Spring AI + OpenAI 兼容 | 2.0.0（`spring-ai-starter-model-openai`，接 LM Studio 本地小模型） |
| JSON | fastjson2 | 2.0.52 |
| 工具 | Lombok | 1.18.46 |
| 开发 | DevTools | — |

**环境约束**：dev/test 必须设 `MYSQL_URL/USER/PASS` 三个环境变量；prod 必须设 `PROD_MAIL_USERNAME/PASSWORD`。

---

## 2. 启动流程

```
1. main() → SpringApplication.run()
2. 推断应用类型（Servlet-based，因引入了 spring-boot-starter-webmvc）
3. @EnableAsync 开启异步支持
4. @MapperScan 扫描 mapper/ 包生成 Mapper 代理
5. 启动嵌入式 Tomcat（dev: 8080 / test: 8080 / prod: 80）
6. 扫描 @Component / @Service / @Controller / @RestController / @Configuration 注册为 Bean
7. 初始化 DispatcherServlet，等待 HTTP 请求
```

应用入口：`BidWinningBackendApplication.java`。详细配置见 `application*.yml`。

---

## 3. 项目结构

```
src/main/java/io/gitee/lujianshi/bidwinningbackend/
├── BidWinningBackendApplication.java       # Spring Boot 启动入口
├── controller/                             # 4 Controller + GlobalExceptionHandler
│   ├── BidRecordController.java            # 投标记录（9 端点：6 标准 CRUD + 1 批量删除 + 1 按公告查询 + 2 AI 风险分析）
│   ├── BidHandlerInfoController.java       # 招标经办人（7 端点：6 标准 CRUD + 1 批量新增）
│   ├── InvitationForBidController.java     # 招标公告（8 端点：6 标准 CRUD + 1 批量新增 + 1 状态变更）
│   ├── StatusCheckController.java          # 服务状态 + 邮件测试（3 端点）
│   └── GlobalExceptionHandler.java         # 全局异常 → Result 包装
├── dto/                                    # 按业务模块分包
│   ├── common/                             # Result / Diff
│   ├── bidhandler/                         # AddRequest / UpdateRequest / BatchAddRequest / DeleteRequest
│   ├── invitationforbid/                   # 同上 + StatusUpdateRequest / BatchDeleteRequest
│   ├── bidrecord/                          # AddRequest / UpdateRequest / BatchDeleteRequest
│   └── statuscheck/                        # PlainTextMailRequest / BidWinningMailRequest
├── entity/                                 # 5 数据库实体：BidHandlerInfo / InvitationForBid / BidRecord / RiskAnalysis / InvitationForBidStatusHistory
├── enums/                                  # 业务枚举：BidNoticeStatus
├── mapper/                                 # @Mapper 接口 extends BaseMapper
├── service/                                # BaseService + 业务 Service
│   ├── BaseService.java                    # 通用 CRUD + 邮件通知
│   ├── BidRecordService.java               # add 方法含 3 次重试逻辑 + listByInvitationId
│   ├── BidHandlerInfoService.java
│   ├── InvitationForBidService.java
│   ├── mail/MailService.java               # SMTP 发送
│   ├── riskanalysis/                       # AI 风险分析
│   │   ├── BidRecordLoader.java            # analyzeRisk 时加载上下文
│   │   └── RiskAnalysisService.java        # 构造器注入 OpenAiChatModel + ChatClient.defaultOptions（Structured Output），调用 LM Studio
│   └── statuscheck/StatusCheckService.java # 中标通知书邮件（异步延迟）
└── utils/                                  # AsyncTaskManager / PromptFiller / ResourceSearcher
```

---

## 4. 7 层开发链路

```
① DB 脚本（src/main/resources/sql/ddl/V*.sql，Flyway 兼容命名，当前未启用 Flyway）
  ↓
② Entity（@TableName + @TableId，主键 @TableId(type = IdType.AUTO)）
  ↓
③ Mapper 接口（@Mapper 注解，extends BaseMapper<T>）
  ↓
④ Mapper XML（namespace = Mapper 接口 FQN，SQL 独立于 Java）
  ↓
⑤ Service(业务 CRUD 型 extends BaseService<T>,子类实现 getMapper() + getIdExtractor();业务流型如 RiskAnalysisService / StatusCheckService 不继承,直接 @Service + 注入依赖)
  ↓
⑥ DTO（@NotBlank / @NotNull / @Size 校验；PATCH 风格 UpdateRequest 仅 @Size）
  ↓
⑦ Controller（@RestController + 路由注解；@Valid 触发校验；统一返 Result<T>）
```

**关键约定**：

| 维度 | 约定 |
|------|------|
| Mapper 接口 | 继承 `BaseMapper<T>`（7 标准方法），手写 XML |
| Service | 业务 CRUD 型继承 `BaseService<T>`,写方法统一 `@Transactional(rollbackFor = Exception.class)`;业务流型不继承,自行管理事务 |
| DTO 命名 | `AddRequest` / `UpdateRequest`（PATCH 风格，仅 @Size）/ `BatchDeleteRequest` / `BatchAddRequest`（含 @Valid 内嵌）/ `StatusUpdateRequest` / `DeleteRequest` |
| Controller 响应 | `Result<T>`；PATCH → `Result<Diff<T>>`；批量删除 → `Result<List<Entity>>` |
| 业务状态码 | 200 成功 / 400 参数错误 / 404 资源不存在 / 500 服务器错误（GlobalExceptionHandler 统一处理） |
| 注入方式 | `jakarta.annotation.Resource`（field 注入） |
| 外键 | 逻辑外键（DB 层不强制 FK 约束） |
| JSON 序列化 | fastjson2（项目无 Jackson） |
| Bean 名冲突 | `JavaMailSender` Bean 名 `mailSender`，自建类不可叫 `MailSender` |

**复用样板**（新增业务表的最小集）：复制 BidHandlerInfo 7 层结构 → 改名 → 改 SQL/字段即可。

---

## 5. 关键类职责

### 5.1 `BaseMapper<T>`（Mapper 基接口）

定义 7 个标准方法：`insert` / `updateById` / `deleteById` / `selectById` / `selectAll` / `deleteByIds` / `selectByIds`。具体 Mapper 继承后绑定对应 XML。

### 5.2 `BaseService<T>`（Service 基类）

封装 7 个标准 CRUD 方法，写方法统一 `@Transactional(rollbackFor = Exception.class)`。子类实现：
- `getMapper()`：返回绑定的 Mapper 实例
- `getIdExtractor()`：返回 `Function<T, Integer>` 用于主键提取（批量删除用）

提供邮件通知方法：
- `sendEmail(T entity, String targetEmail, String endpoint, String operation)`：4-param 版
- `sendEmail(T entity, String endpoint, String operation)`：2-param 重载，反射从 entity 读 targetEmail
- `sendBatchEmail(List<T> entities, String outerTargetEmail, String endpoint, String operation)`：批量版

### 5.3 `Result<T>`（统一响应包装）

`code` / `message` / `data` 三字段。静态工厂：
- `Result.success(T data, String message)`：成功
- `Result.error(Integer code, String message)`：业务失败

### 5.4 `Diff<T>`（PATCH diff 包装）

`before`（DB 原值）+ `after`（更新后值），泛型适配任意实体。

### 5.5 `GlobalExceptionHandler`（全局异常处理）

`@RestControllerAdvice` + `@ExceptionHandler`：
- `MethodArgumentNotValidException`（@Valid 校验失败）→ 400 + 第一个错误消息
- `IllegalArgumentException` → 400 + 异常消息
- 其他 `Exception` → 500 + "服务器内部错误"（不泄漏堆栈）

### 5.6 `AsyncTaskManager`（异步任务）

```java
public static void execute(Runnable task, long delayMillis)
```

`new Thread + .start()` 纯 Java 线程，非 Spring Bean。支持延迟执行。生产环境高频调用建议改用线程池。

### 5.7 `PromptFiller`（AI prompt 模板填充）

- `loadFromResource(String path)`：加载静态模板（无占位符）
- `fillFromResource(String path, Map<String, String> vars)`：加载并替换 `{{key}}` 占位符

### 5.8 `RiskAnalysisService`（AI 风险分析）

构造器注入 `OpenAiChatModel`，构造器里用 `ChatClient.builder(model).defaultOptions(opts).build()` 装载 Structured Output JSON Schema 后缓存为 `final` 字段。`analyzeRisk(Integer bidRecordId)` 同步调 LM Studio、Schema 约束后 JSON 解析、落库（V18 已改字段引用：`tendererId` → `bidRecordId`）。

### 5.9 `BidRecordService`（重试逻辑）

`add(entity)` 必须在事务内做 3 次重试，每次重试前先查 `(invitation_id, bidder_name)` 下的 `MAX(bid_no)` 计算 `bidNo = max + 1`（null 时为 1），再 INSERT。捕获 `DuplicateKeyException`（并发同号）后重试。3 次后仍冲突抛 `IllegalStateException`。

---

## 6. 异常处理链路

```
Controller 业务方法
  ↓ 业务异常
抛 IllegalArgumentException
  ↓
@RestControllerAdvice GlobalExceptionHandler.handleIllegalArgument
  ↓
返 Result.error(400, e.getMessage())
  ↓
前端收到 400 业务码

@Valid 校验失败
  ↓
Spring 抛 MethodArgumentNotValidException
  ↓
GlobalExceptionHandler.handleValidation
  ↓
返 Result.error(400, 第一个校验错误消息)

未捕获异常
  ↓
GlobalExceptionHandler.handleUnknown
  ↓
返 Result.error(500, "服务器内部错误")
```

---

## 7. 异步任务（AsyncTaskManager）

### 7.1 使用场景

| 场景 | 调用方 | 延迟 |
|------|--------|------|
| 中标通知书 HTML 邮件 | `StatusCheckService.sendNotification` | 5000ms（让前端展示"发送中"） |
| CRUD 结果通知邮件 | `BaseService.sendEmail` 内部 | 0ms（CRUD 后立即异步发） |
| AI 风险分析（未来） | — | — |

### 7.2 时序

```
主线程:
  ① Controller 业务执行 → service.add(entity) 落库
  ② service.sendEmail(entity) → 反射读 targetEmail + 构造 Thymeleaf Context
  ③ AsyncTaskManager.execute(runnable, 0)
       ├─ new Thread(runnable).start()  ← 构造 + 启动，瞬间完成
       └─ 主线程继续
  ④ 包装 Result.success() 并 return
  ⑤ HTTP 响应完成（前端收到 200）

子线程（与主线程并行）:
  ⑥ ResourceSearcher.validateTemplateExists
  ⑦ SpringTemplateEngine.process 渲染 HTML
  ⑧ MailService.sendHtmlEmail SMTP 发送
  ⑨ try-catch 兜底，失败仅日志告警，不影响主业务
```

**关键点**：
- 主线程不阻塞（`new Thread().start()` 内部构造+启动约微秒级）
- 子线程异常隔离（try-catch 兜底）
- targetEmail 为 null/空时主线程直接 return，不提交异步任务

---

## 8. 邮件通知（Service 层 BaseService.sendEmail）

> **历史方案（AOP 切面）已废弃**。当前由 Service 层显式调用，统一由 `BaseService.sendEmail` 编排。

### 8.1 触发流程

```
Controller 业务方法末尾:
  service.add(entity);
  service.sendOperationEmail(entity);   ← 1
  return Result.success(entity, "创建成功");

  1. 子 Service.sendOperationEmail
     → this.sendEmail(entity, "xxx/create", "新增")
     → BaseService.sendEmail 反射 + 异步
```

### 8.2 接入新端点（3 步）

**Step 1**：DTO 加 `targetEmail` 字段（必填）
```java
@NotBlank private String targetEmail;
```

**Step 2**：子 Service 加便利方法
```java
public void sendOperationEmail(Xxx entity) {
    this.sendEmail(entity, "xxx/create", "新增");
}
```

**Step 3**：Controller 业务方法末尾显式调用
```java
service.add(entity);
service.sendOperationEmail(entity);
```

### 8.3 当前已接入端点

| Controller | 端点 | 触发方法 |
|---|---|---|
| `BidHandlerInfoController` | `POST /bid-handler-info/add` | `service.sendOperationEmail(entity)` |
| 其他 26 个端点 | — | 未发送 |

### 8.4 邮件模板

`templates/crud-email-template.html`，变量：

| 变量 | 来源 |
|------|------|
| `subject` | `BaseService.sendEmail` 内部构造 |
| `formName` | `endpoint` 参数 |
| `actionName` | `operation` 参数 |
| `operateTime` | `LocalDateTime.now()` |
| `status` | `operation + " 成功"` |
| `data` | `PropertyDescriptor` 反射遍历 entity 字段（排除 targetEmail） |

### 8.5 收件人解析

`targetEmail` 来自 `entity.targetEmail`（已落库），由 `BaseService.sendEmail` 反射读取。`null`/空字符串时跳过发邮件。

---

## 9. 关键设计决策（防坑指南）

| 决策 | 理由 |
|------|------|
| **Mapper XML 而非注解 SQL** | 坚持 Java/SQL 分离，便于 DBA 维护 |
| **业务 Service 分类** | 业务 CRUD 型(3 个:BidHandlerInfo/InvitationForBid/BidRecord)继承 `BaseService` 消除重复;业务流型(2 个:RiskAnalysis/StatusCheck)不继承,因业务逻辑独立,避免被通用 CRUD API 污染(同 `StatusCheckService` 先例) |
| **DTO 每端点独立** | 避免端点间耦合，符合阿里《Java 开发手册》 |
| **PATCH 语义** | 调用方传哪些字段就更新哪些字段，null = 不修改 |
| **批量删除 data 返实体列表** | 前端 diff 友好，多 1 次 SELECT（PK 索引，毫秒级）|
| **不级联删除** | 应用层不保证外键关系，明细行可能变孤儿（业务层负责）|
| **PageHelper.startPage + finally clearPage** | ThreadLocal 写入分页参数，避免线程池污染 |
| **Page<Object> 不用 try-with-resources** | PageHelper 的 Page 不实现 AutoCloseable，用 `//noinspection try` |
| **fastjson2 而非 Jackson** | 项目已引入复用，避免新增依赖 |
| **MailService 而非 MailSender** | 避免与 Spring Boot 自动配置的 `JavaMailSender`（Bean 名 mailSender）同名冲突 |
| **AI OpenAiChatModel 注入 + ChatClient.defaultOptions** | 符合 Spring AI 2.0 官方推荐；注入 `OpenAiChatModel`，在 `ChatClient.builder(...).defaultOptions(opts).build()` 中装载 Structured Output JSON Schema 约束，缓存为 final 字段 |
| **bid_record 投标次数服务端生成** | bidNo = max(bid_no)+1，3 次重试防并发同号，UNIQUE 兜底 |
| **不暴露 bid_no 给客户端** | Client 只传 invitationId / bidderName / quotePrice / bidderContact，服务端算 bidNo |

---

## 10. 新增业务表的复用模式

以新增 `Xxx` 表为例：

1. **DB**：`sql/ddl/V{N}__create_xxx.sql`
2. **Entity**：`entity/Xxx.java`（@TableName + @TableId + Lombok 三件套）
3. **Mapper 接口**：`mapper/XxxMapper.java extends BaseMapper<Xxx>`（@Mapper 注解）
4. **Mapper XML**：`resources/mapper/XxxMapper.xml`（namespace = FQN，7 个标准 SQL 标签 + 可选 `<selectByXxxId>`）
5. **Service**:`service/XxxService.java` — 业务 CRUD 型 `extends BaseService<Xxx>`(getMapper + getIdExtractor 两个方法);业务流型不继承,直接 `@Service` + 注入依赖
6. **DTO**：`dto/xxx/{AddRequest, UpdateRequest, BatchDeleteRequest}.java`
7. **Controller**：`controller/XxxController.java`（6 标准 CRUD 端点：add / getById / patch / delete / list / batch-delete；可选 batch-add / status / AI 子端点）
8. **AI 风险分析（如需要）**：`service/riskanalysis/XxxRiskAnalysisService.java`（参考 RiskAnalysisService）

---

## 11. 启动命令速查

```bash
# dev（默认）
export MYSQL_URL='jdbc:mysql://...:3306/bid-winning?...' \
       MYSQL_USERNAME=jianshi \
       MYSQL_PASSWORD=xxx \
       DEEPSEEK_API_KEY=sk-xxx \
       MAIL_PASSWORD=xxx
./mvnw spring-boot:run

# test
./mvnw spring-boot:run -Dspring-boot.run.profiles=test

# prod
export SPRING_PROFILES_ACTIVE=prod
export MYSQL_URL=... MYSQL_USERNAME=... MYSQL_PASSWORD=...
export PROD_MAIL_USERNAME=... PROD_MAIL_PASSWORD=...
./mvnw spring-boot:run
```

详细配置见 AGENTS.md "环境变量配置" 章节。

---

## 12. 关联文档

- AI Structured Output 链路说明:[`docs/structured-output.md`](structured-output.md) — Spring AI + LM Studio + 本地模型 Structured Output 端到端链路、GBNF grammar 原理、调优手段
- 完整架构图(6 tab, C4 L1~L3 + 分层 + 时序 + ER):[`docs/architecture-diagram.drawio`](architecture-diagram.drawio) — 用 draw.io 打开,6 tab 全景
