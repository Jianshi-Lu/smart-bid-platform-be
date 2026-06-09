# AGENTS.md

## Build & Run

- Use `./mvnw` (Maven wrapper included) - do not require system Maven
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test`
- Package: `./mvnw clean package`

## Tech Stack

- Java 25 (required)
- Spring Boot 4.0.6 with `spring-boot-starter-webmvc` (servlet-based, not reactive)
- Lombok for boilerplate reduction
- DevTools enabled for runtime reloading

## Project Structure

- Single module Maven project
- Standard layout: `src/main/java`, `src/main/resources`, `src/test/java`
- Source root package: `io.gitee.lujianshi.bidwinningbackend`
- Layered architecture:
  - `controller/` - HTTP 路由层（@RestController + @GetMapping / @PostMapping）
    - HelloController, PlainTextMailController, BidWinningMailController
  - `service/` - 业务逻辑层（@Service + @Value）
    - HelloService - 演示服务
    - PlainTextMailSender - 纯文本邮件发送（SimpleMailMessage）
    - BidWinningMailSender - HTML 邮件发送（MimeMessageHelper）
    - BidWinningTemplateFiller - Thymeleaf 模板渲染
    - StaticResourceSearcher - 模板文件存在性验证（精确匹配）
    - MailLoggingService - MD 格式日志格式化
  - `dto/` - 数据传输对象（@Data + @NoArgsConstructor + @AllArgsConstructor）
    - PlainTextMailRequest - 纯文本邮件请求
    - BidWinningMailRequest - 中标通知书请求（extends PlainTextMailRequest）
  - `utils/` - 工具类
    - AsyncTaskManager - 延迟异步任务执行器（纯 Java 线程，非 Spring Bean）
  - `templates/` - Thymeleaf 模板文件（src/main/resources/templates/）

## Entry Point

- `BidWinningBackendApplication.java` — Spring Boot 启动入口（纯 bootstrap，无路由）
- Dev server: `http://localhost:8079`
- 配置端口: `server.port=8079`（application-dev.yml）

## Profiles（多环境配置）

- `application.yml` — 公共配置（应用名、Thymeleaf 路径）+ 默认 profile
- `application-dev.yml` — 开发环境（默认，端口 8079）
- `application-test.yml` — 测试环境（端口 8080）
- `application-prod.yml` — 生产环境（端口 80，敏感信息走环境变量）

**切换方式**：
1. 修改 `application.yml` 中 `spring.profiles.active`
2. 启动命令：`./mvnw spring-boot:run -Dspring-boot.run.profiles=test`
3. 环境变量：`export SPRING_PROFILES_ACTIVE=prod`

## API Endpoints

| Method | Path | Content-Type | Description |
|-------|------|-------------|-------------|
| GET | /hello | - | 返回 "Hello World!" |
| GET | /access-url | - | 重定向到 custom.url |
| POST | /fetch-url | - | 异步触发延迟任务，同步返回 URL |
| POST | /send-email | application/x-www-form-urlencoded | 发送纯文本邮件 |
| POST | /send-notification | application/json | 发送中标通知书 HTML 邮件（异步延迟5秒返回"发送成功"） |

## Email

### POST /send-email（纯文本）

- Content-Type: `application/x-www-form-urlencoded`
- 参数: `to`（收件人）、`subject`（主题）、`content`（内容）
- 调用链: PlainTextMailController → PlainTextMailSender.sendEmail() → JavaMailSender.send()

### POST /send-notification（中标通知书 HTML）

- Content-Type: `application/json`
- 参数（JSON body）:
  - `to` — 收件人邮箱
  - `subject` — 邮件主题
  - `projectCode` — 项目编号
  - `handlerName` — 经办人
  - `bidWinningList` — 中标结果列表 `[{supplierName, bidWinningAmount}, ...]`
  - `templateName` — 模板文件名（不含 .html 后缀，如 "bid-winning-notification"）
- 调用链:
  1. BidWinningTemplateFiller.processTemplate() — 渲染模板（Thymeleaf）
  2. **立即返回** "发送成功"（主线程不阻塞）
  3. AsyncTaskManager.execute(..., 5000) — 延迟5秒后异步执行
  4. MailLoggingService.formatMailLog() — 打印 MD 格式日志到控制台
  5. BidWinningMailSender.sendHtmlEmail() — 发送 HTML 邮件
- 模板搜索: 精确匹配 `classpath:/templates/{templateName}.html`，不存在则抛 IllegalArgumentException
- 模板变量: `th:text="${projectCode}"`、`th:text="${handlerName}"`、`th:each` 渲染 bidWinningList

## Async Task

- `AsyncTaskManager.execute(Runnable task, long delayMillis)` — 纯 Java 线程实现的延迟异步任务
- 非 Spring Bean，纯静态工具类（每次调用创建新 Thread）
- 使用场景: 邮件延迟5秒后发送（BidWinningMailController）
- 优势: 无需 `@EnableAsync` + Spring AOP 代理，支持延迟执行
- 注意: 高频调用建议改用线程池（当前实现每次 new Thread 有创建开销）
- finally 兜底: try-catch 块后增加 finally 输出任务完成日志（不影响业务，仅状态记录）

## DTO

### PlainTextMailRequest

- 字段: `to`、`from`、`subject`、`content`
- 使用场景: POST /send-email（纯文本邮件）

### BidWinningMailRequest extends PlainTextMailRequest

- 继承字段: `to`、`from`、`subject`
- 自有字段: `projectCode`、`handlerName`、`bidWinningList`、`templateName`
- 内部类: `BidWinningListItem(supplierName, bidWinningAmount)`
- 使用场景: POST /send-notification（中标通知书 HTML 邮件）

## Testing

- `@SpringBootTest` for integration tests
- Default test verifies context loads

## Lombok

- Annotation processor enabled in maven-compiler-plugin
- IDEs need Lombok plugin to resolve compile-time annotations
- 常用注解: `@Data`（getter/setter/equals/hashCode/toString）、`@NoArgsConstructor`、`@AllArgsConstructor`