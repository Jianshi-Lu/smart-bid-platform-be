# AGENTS.md

> bid-winning-backend 项目的 Agent 工作约定。详细架构/API/表结构/开发流程见 `docs/` 下各 MD。
>
> **本文件以"代码规范"为核心**，业务实体/端点仅做简要索引，详细以具体业务代码为准。

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
- **Spring AI 2.0.0** + `spring-ai-starter-model-deepseek`（AI 风险分析）
- **DevTools**（运行时重载）

## Project Structure（简要索引）

> 详细业务字段/方法以具体源码为准。

```
src/main/java/io/gitee/lujianshi/bidwinningbackend/
├── BidWinningBackendApplication.java       # Spring Boot 启动入口
├── controller/                             # 4 Controller + GlobalExceptionHandler
├── dto/                                    # 按业务模块分包
│   └── common/                             # Result / Diff
├── entity/                                 # TenderHandlerInfo / TenderNotice / SupplierDetail / RiskAnalysis
├── mapper/                                 # @Mapper 接口，extends BaseMapper
├── service/                                # BaseService + 各业务模块
│   ├── mail/                               # MailService
│   ├── riskanalysis/                       # AI 风险分析
│   └── statuscheck/                        # 中标通知书
└── utils/                                  # AsyncTaskManager / PromptFiller / ResourceSearcher

src/main/resources/
├── mapper/                                 # MyBatis XML
├── prompts/                                # AI prompt 模板（*.txt）
├── sql/ddl/                                # Flyway 兼容 V1-V11，**未启用 Flyway**
├── templates/                              # Thymeleaf 邮件模板
└── application*.yml                        # dev / test / prod 三套配置
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

| 变量 | 必填 | 用途 |
|------|:----:|------|
| `MYSQL_URL` | ✅ dev/test | JDBC URL |
| `MYSQL_USERNAME` | ✅ dev/test | DB 用户名 |
| `MYSQL_PASSWORD` | ✅ dev/test | DB 密码 |
| `DEEPSEEK_API_KEY` | ✅ dev | AI 风险分析 |
| `MAIL_PASSWORD` | ⚠️ dev | 发件邮箱授权码 |
| `PROD_MAIL_USERNAME` | ⚠️ prod | 发件邮箱 |
| `PROD_MAIL_PASSWORD` | ⚠️ prod | 邮箱授权码 |

> ⚠️ dev/test 必须设 `MYSQL_*` 三个变量；prod 必须设 `PROD_MAIL_*`。

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
| dev 切到 prod | 缺 `${PROD_MAIL_*}` 占位符报错 | 加环境变量或切回 dev profile |
| 缺 `allowPublicKeyRetrieval=true` | `Public Key Retrieval is not allowed` | URL 末尾加 `&allowPublicKeyRetrieval=true` |

## API Endpoints（简要索引）

> 完整规范见 `docs/api.md`，每个端点请求/响应以具体 Controller 为准。

### 业务 CRUD

| 模块 | 端点数 | 路径前缀 |
|------|:---:|------|
| 经办人 (TenderHandlerInfo) | 6 | `/tender-handler-info/...` |
| 定标通知 (TenderNotice) | 6 | `/tender-notice/...` |
| 供应商明细 (SupplierDetail) | 6 | `/supplier-detail/...` |
| **AI 风险分析** | 2 | `/supplier-detail/{action}/{id}` |

### 服务状态 + 邮件测试

| Method | Path | 说明 |
|-------|------|------|
| GET | `/service-status` | Spring 服务存活探活 |
| POST | `/send-email` | 纯文本邮件 |
| POST | `/send-notification` | 中标通知书 HTML 邮件（异步延迟5秒） |

### AI 风险分析端点

| Method | Path | 说明 |
|-------|------|------|
| POST | `/supplier-detail/analyze-risk/{id}` | 同步调用 DeepSeek，落库，返全字段 `RiskAnalysis` |
| GET | `/supplier-detail/risk-history/{id}` | 历史分析记录（按时间倒序） |

## Async Task（`AsyncTaskManager`）

- 静态方法 `execute(Runnable task, long delayMillis)`
- 纯 Java 线程，非 Spring Bean（每次 `new Thread().start()`）
- 使用场景：`/send-notification` 延迟 5 秒发邮件
- 注意：高频调用建议改用线程池

## JavaDoc 注释规范

参考阿里/腾讯大厂规约，落地到本项目。

### 基本结构

- 注释以 `/**` 开头，`*/` 结束
- 必须位于类、方法、字段声明**之前**
- 默认 javadoc 命令只对 `public` / `protected` 生效（IDE 通常也为 `private` 生成）

### 必须写 JavaDoc 的场景

| 元素 | 是否必须 | 说明 |
|------|:---:|------|
| Controller / Service / Component | ✅ 强推荐 | 类级 JavaDoc 必写 |
| 公共 API 方法 | ✅ 强推荐 | 已在项目内执行 |
| 公共常量字段 | ✅ 推荐 | 说明业务含义 |
| 简单 getter/setter | ❌ 可省略 | 除非有特殊业务含义 |
| 私有方法 | ⚠️ 视复杂度 | 关键逻辑写 |

### 标签使用规范

| 标签 | 用途 | 必填场景 |
|------|------|---------|
| `@param` | 方法参数说明 | 每个参数必填 |
| `@return` | 返回值说明 | 非 void 方法必填 |
| `@throws` / `@exception` | 可能抛出的异常 | 显式抛异常时必填 |
| `@see` | 关联类/方法 | 引用其他类时推荐 |
| `@since` | 引入版本 | 新增 API 时必填 |
| `@deprecated` | 已弃用 | 保留说明 + 替代方案 |
| `@author` | 作者 | 团队约定 |

### 标准模板

```java
/**
 * 一句话功能描述（首句作索引摘要，应能脱离上下文理解）
 *
 * <p>详细描述：业务背景、设计决策、关键约束（可选）</p>
 *
 * <h3>使用示例</h3>（可选）
 * <pre>
 * service.doXxx(arg1, arg2);
 * </pre>
 *
 * @param a 参数含义 + 取值范围
 * @param b 参数含义
 * @return 返回值含义 + 可能的取值
 * @throws IllegalArgumentException 触发条件
 * @see RelatedClass
 * @since 1.0
 */
```

### 编写原则（"黄金法则"）

1. **解释"为什么"，而非重复"做什么"**：命名已表达意图，注释补充上下文
2. **避免冗余**：不要对 getter/setter 过度描述
3. **首句作摘要**：第一句话决定文档索引的可读性
4. **与代码同步**：方法签名变了必须更新注释
5. **边界条件必标**：null / 空集合 / 负数等特殊输入
6. **线程安全必标**：并发场景明确说明
7. **避免噪音注释**：`// 设置用户名` 对应 `user.setName(name)` 是反模式

### HTML 标签（JavaDoc 支持）

| 标签 | 用途 | 推荐度 |
|------|------|:---:|
| `<p>` | 段落 | ✅ |
| `<pre>` | 代码块（保留缩进） | ✅ |
| `<code>` | 行内代码 | ✅ |
| `<ul>/<li>` | 列表 | ✅ |
| `<h3>` | 子标题 | ✅ |
| `<table>` | 表格 | ⚠️ 复杂，可读性差 |

### IDE 工具

- **IntelliJ IDEA**：`/** + Enter` 自动生成标签骨架
- **Easy Javadoc 插件**：智能翻译 + 自动生成（可选）
- **Alibaba Java Guidelines**：CI 集成 Checkstyle 检查（可选）

## 关键设计约定

| 维度 | 约定 |
|------|------|
| Mapper 接口 | 继承 `BaseMapper<T>`（项目自建泛型基接口），手写 XML SQL |
| Service | 继承 `BaseService<T>`（项目自建泛型基类），子类实现 `getMapper()` + `getIdExtractor()` |
| 写方法事务 | `@Transactional(rollbackFor = Exception.class)`（由 `BaseService` 统一加） |
| DTO 命名 | `<Entity>AddRequest` / `<Entity>UpdateRequest`（PATCH 风格，**仅 `@Size`，无 `@NotBlank`/`@NotNull`**）/ `<Entity>BatchDeleteRequest` |
| Controller 响应 | `Result<T>` 包装；PATCH → `Result<Diff<T>>`；批量删除 → `Result<List<Entity>>` |
| 业务状态码 | 200=成功, 400=参数错误, 404=资源不存在 |
| 注入方式 | `jakarta.annotation.Resource`（field 注入） |
| 全局异常处理 | `GlobalExceptionHandler` 统一捕获，返回 `Result` 包装 |
| 外键 | `tender_handler_id` / `notice_id` 为**逻辑外键**，DB 层不强制 FK 约束 |
| Flyway | **未**启用，仅借用 V*.sql 命名 |
| JSON 序列化 | fastjson2（项目无 Jackson） |
| Bean 名冲突 | `JavaMailSender` Bean 名 `mailSender`，自建类**不可**叫 `MailSender`（会冲突） |
| `Page<Object>` | 不实现 `AutoCloseable`，**不可**用 try-with-resources |
| Spring AI 实践 | 构造器注入 `ChatClient.Builder`，构造器里 `build()` 一次缓存为 `final` 字段 |

## 端到端开发流程（7 层链路）

> 详见 `docs/development-flow.md`（以 TenderHandlerInfo 为完整示例）

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