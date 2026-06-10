# 项目架构文档

> 本文档深入分析 bid-winning-backend 项目的内部架构，涵盖 Spring Boot 启动流程、实体类设计、Controller 层、Service 层和工具类的实现细节。

---

## 1. Spring Boot 启动流程

### 1.1 程序入口：`main()` 方法

```
BidWinningBackendApplication.java:27
```

```java
public static void main(String[] args) {
    SpringApplication.run(BidWinningBackendApplication.class, args);
}
```

这是整个项目的起点。当执行 `./mvnw spring-boot:run` 或在 IDE 中点击 Run 时，JVM 首先执行 `main()` 方法。

`SpringApplication.run()` 做了两件事：
1. 创建 `SpringApplication` 实例（传入 `BidWinningBackendApplication.class`，记作"主配置类"）
2. 调用该实例的 `run()` 方法

---

### 1.2 `SpringApplication.run()` 内部执行流程

`SpringApplication.run()` 的执行分为多个阶段，按顺序如下：

#### 阶段 1：推断应用类型（DeduceFromClasspath）

Spring Boot 通过检查 classpath 中是否存在特定类来推断当前是 Servlet 应用还是 Reactive 应用：

```
Servlet 类存在（jakarta.servlet.Servlet）→ Spring MVC（servlet-based）
Reactor 类存在（reactor.core.publisher.Publisher）→ WebFlux（reactive）
都不存在 → 非 Web 应用
```

本项目引入的是 `spring-boot-starter-webmvc`，属于 **Servlet-based** 应用。

#### 阶段 2：加载自动配置

Spring Boot 4.x 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（旧版用 `spring.factories`）中注册了大量自动配置类（Auto Configuration）。

这些配置类在启动阶段会被逐一加载、评估、决定是否生效。

#### 阶段 3：创建 `ApplicationContext`（IOC 容器）

```java
// Spring Boot 内部简化逻辑
DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
ApplicationContext context = new AnnotationConfigApplicationContext(beanFactory);
```

实际上 Spring Boot 会根据应用类型创建不同的容器：
- Servlet 应用 → `AnnotationConfigServletWebServerApplicationContext`
- Reactive 应用 → `AnnotationConfigReactiveWebServerApplicationContext`

#### 阶段 4：启动嵌入式 Servlet 容器

Spring Boot 默认使用嵌入式 Tomcat（NioEndpoint）。

容器不仅负责托管 Spring Bean，还负责监听 HTTP 请求并分发给 `DispatcherServlet`。

---

### 1.3 `@SpringBootApplication` 三合一注解原理

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { ... })
public @interface SpringBootApplication {
    // ...
}
```

| 注解 | 作用 |
|------|------|
| `@SpringBootConfiguration` | 本质是 `@Configuration`，标记此类为 Java Config 配置类，作为容器 Bean 注册的来源之一 |
| `@EnableAutoConfiguration` | 启用自动配置，让 Spring Boot 根据 classpath 和条件注解（`@Conditional`）自动配置第三方库 |
| `@ComponentScan` | 组件扫描，从主配置类所在包（`io.gitee.lujianshi.bidwinningbackend`）向下扫描，收集 `@Component`、`@Service`、`@Controller` 等标注的类注册为 Spring Bean |

**关键点**：`@ComponentScan` 默认只扫描主配置类所在的包及其子包。这就解释了为什么 `BidWinningBackendApplication` 放在 `io.gitee.lujianshi.bidwinningbackend` 包下，所有子包（controller、service、dto、entity、utils）中的组件都能被自动发现。

---

### 1.4 `@ComponentScan` 组件扫描流程

1. Spring 发现 `@ComponentScan` 后，立即开始扫描
2. 扫描范围：从主配置类所在包向下递归，匹配所有 `.class` 文件
3. 扫描到的类若带有以下注解之一，则注册为 Bean：
   - `@Component`
   - `@Repository`
   - `@Service`
   - `@Controller`
   - `@Configuration`

本项目中注册为 Bean 的类：

| 类 | 注解 |
|----|------|
| `HelloService` | `@Service` |
| `PlainTextMailSender` | `@Service` |
| `StaticResourceSearcher` | `@Service` |
| `BidWinningMailSender` | `@Service` |
| `BidWinningTemplateFiller` | `@Service` |
| `MailLoggingService` | `@Service` |
| `HelloController` | `@RestController`（含 `@Controller`） |
| `PlainTextMailController` | `@RestController`（含 `@Controller`） |
| `BidWinningMailController` | `@RestController`（含 `@Controller`） |
| `BidWinningBackendApplication` | `@Configuration`（隐含） |

`AsyncTaskManager` **没有**任何上述注解，它是一个纯静态工具类，不归 Spring 容器管理。

**Bean 注册时机**：Bean 的创建（instantiation）在容器 refresh 阶段进行。Spring 通过 `BeanFactory` 接口统一管理所有 Bean 的创建、初始化和依赖注入。

---

### 1.5 `@EnableAutoConfiguration` 自动配置详解

`@EnableAutoConfiguration` 是 Spring Boot 最核心的能力之一。它通过 `AutoConfigurationImportSelector` 读取 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（或旧版 `META-INF/spring.factories`）中列出的自动配置类。

对于本项目，以下自动配置类参与了启动过程：

#### 1.5.1 `MailSenderAutoConfiguration`

**配置时机**：检测到 classpath 中存在 `spring-boot-starter-mail` 且存在 `JavaMailSenderImpl` 类时生效。

**做了什么**：
1. 读取 `application.properties` 中以 `spring.mail.` 开头的配置项
2. 创建 `JavaMailSenderImpl` 实例（实现 `JavaMailSender` 接口）
3. 设置 SMTP 参数：`host`、`port`、`username`、`password`、`default-encoding`
4. 配置 `Authenticator`（如果需要）
5. 注册为容器中唯一的 `JavaMailSender` Bean

**关键结果**：
```
JavaMailSender mailSender  ← PlainTextMailSender / BidWinningMailSender 通过 @Autowired 注入
```

#### 1.5.2 `ThymeleafAutoConfiguration`

**配置时机**：检测到 classpath 中存在 `spring-boot-starter-thymeleaf` 时生效。

**做了什么**：
1. 读取 `spring.thymeleaf.*` 配置（`prefix`、`suffix`、`mode`、`encoding`）
2. 创建 `SpringTemplateEngine` 实例（Thymeleaf 的核心渲染引擎）
3. 配置 `TemplateResolver`：根据 `prefix` + 模板名 + `suffix` 拼接完整路径
   - `prefix` = `classpath:/templates/`（本项目配置）
   - `suffix` = `.html`（本项目配置）
   - 因此模板名 `"bid-winning-notification"` 解析为 `classpath:/templates/bid-winning-notification.html`
4. 注册为容器中唯一的 `SpringTemplateEngine` Bean

**关键结果**：
```
SpringTemplateEngine templateEngine  ← BidWinningTemplateFiller 通过 @Autowired 注入
```

#### 1.5.3 `WebMvcAutoConfiguration`

**配置时机**：当引入 `spring-boot-starter-webmvc` 时自动生效。

**做了什么**：
1. 配置 `DispatcherServlet`（Spring MVC 的前端控制器）
2. 注册 `HandlerMapping`（URL 到 Controller 方法的映射表，默认 `RequestMappingHandlerMapping`）
3. 注册 `HandlerAdapter`（用于调用 Controller 方法，默认 `RequestMappingHandlerAdapter`）
4. 注册 `ViewResolver`（视图解析器，用于渲染模板，本项目未使用）
5. 配置 `MessageCodesResolver`（用于绑定错误消息）
6. 添加 `FunctionalBean`（如 `methodParameterNameDiscoverer`）

**关键结果**：
```
DispatcherServlet  ← 所有 HTTP 请求的入口
RequestMappingHandlerMapping  ← /hello → HelloController.hello() 的映射关系在此注册
RequestMappingHandlerAdapter  ← 调用 Controller 方法的适配器
```

---

### 1.6 嵌入式 Servlet 容器（Tomcat）启动

Spring Boot 默认使用嵌入式 Tomcat。启动流程如下：

#### 步骤 1：创建 Tomcat 实例

```java
Tomcat tomcat = new Tomcat();
tomcat.setPort(8079);  // 从 application.properties 读取
tomcat.getConnector();  // 初始化 connector（NIO 模式）
```

#### 步骤 2：创建 Context（应用上下文）

Tomcat 为每个 Web 应用创建一个 `StandardContext`，对应本项目的 Servlet 应用。

#### 步骤 3：注册 DispatcherServlet

Spring Boot 将 `DispatcherServlet` 注册到 Tomcat 的 `Context` 中：

```
DispatcherServlet  ←  url-mapping: /
                        ↓
                接收所有 HTTP 请求（POST /send-email, GET /hello 等）
```

#### 步骤 4：启动 NIO Endpoint

```
NioEndpoint.start()
  → Poller线程 ← 监听 8079 端口的 socket 连接
  → Request 处理线程池
  → CoyoteAdapter.service() ← 将请求传递给 Tomcat pipeline
  → ApplicationFilterChain ← 执行 Filter 链
  → DispatcherServlet.doDispatch() ← Spring MVC 分发
```

Tomcat 使用 NIO 模式（`NioEndpoint`），一个 Poller 线程轮询多个 socket，接收连接后分发到线程池处理，相比传统的 BIO（每连接一线程）更高效。

#### 步骤 5：请求分发给 DispatcherServlet

当 HTTP 请求进入 `DispatcherServlet.doDispatch()` 后，Spring MVC 的请求处理链路如下：

```
DispatcherServlet.doDispatch(HttpServletRequest, HttpServletResponse)
  │
  ├─ 1. HandlerMapping.getHandler(request)
  │     ↓
  │     遍历所有 HandlerMapping 实现（默认 RequestMappingHandlerMapping）
  │     根据 HTTP method + URL 匹配到对应的 HandlerMethod（HelloController.hello）
  │     返回 HandlerExecutionChain（包含 Handler + Interceptor列表）
  │
  ├─ 2. HandlerAdapter.supports(handler) → RequestMappingHandlerAdapter
  │
  ├─ 3. HandlerAdapter.handle(request, response, handler)
  │     ↓
  │     → ArgumentResolvers：根据参数类型，从 request 中解析参数值
  │        例如：BidWinningMailController.sendNotification(BidWinningMailRequest request)
  │        → Spring 通过 data binding 将 form-data 字段映射到 BidWinningMailRequest 对象
  │     → HandlerMethod.invoke()：调用 Controller 方法
  │     → ReturnValueHandlers：将方法返回值转换为响应（直接写入 body 或渲染视图）
  │
  └─ 4. 执行 Interceptor.postHandle()（如果有）
```

---

### 1.7 `@EnableAsync` 异步机制

```java
@EnableAsync   // BidWinningBackendApplication.java:24
public class BidWinningBackendApplication { ... }
```

`@EnableAsync` 的作用是**开启 Spring AOP 代理模式**，使得标注了 `@Async` 的方法能够被异步执行。

#### 工作原理

`@EnableAsync` 会向容器注册一个 `AsyncAnnotationBeanPostProcessor`（一个 `BeanPostProcessor`）。

**关键机制**：
1. `BeanPostProcessor` 在每个 Bean 创建完成后，判断该 Bean 是否有 `@Async` 方法
2. 如果有，Spring 为该 Bean **创建一个 AOP 代理**（`JdkDynamicAopProxy` 或 `CglibAopProxy`）
3. 代理拦截所有 `@Async` 方法调用，将其包装为 `AsyncTask` 提交到线程池执行
4. 调用方立即返回，实际执行在线程池中进行

#### 本项目中的 @Async 使用

```java
// HelloService.java
@Async
public void fetchAfterDelay() {
    AsyncTaskManager.execute(() -> { ... }, 3000);
}
```

Spring AOP 代理在调用 `fetchAfterDelay()` 时：
- 不会在当前线程执行
- 而是将任务提交到 `SimpleAsyncTaskExecutor`（`@EnableAsync` 默认使用的执行器）
- 主线程立即返回，3 秒后在新线程中打印"获取地址成功"

**注意**：`HelloService.fetchAfterDelay()` 内部又调用了 `AsyncTaskManager.execute()`，相当于在 `@Async` 新线程中再次创建新线程（双重异步，存在设计冗余）。

---

### 1.8 Bean 创建顺序与依赖注入

Spring 容器在 refresh 阶段按照一定顺序创建 Bean：

```
1. BeanFactoryPostProcessor（最早，可修改 BeanDefinition）
   ↓
2. BeanPostProcessor（创建 Bean 实例前后的拦截）
   ↓
3. 普通 Bean（按依赖关系排序）
```

本项目的 Bean 创建大致顺序：

| 顺序 | Bean 类 | 依赖 |
|------|---------|------|
| 1 | `JavaMailSenderImpl` | 无（由 `MailSenderAutoConfiguration` 创建） |
| 2 | `SpringTemplateEngine` | 无（由 `ThymeleafAutoConfiguration` 创建） |
| 3 | `HelloService` | `@Value` 注入配置值 |
| 4 | `PlainTextMailSender` | `JavaMailSender`、`@Value` |
| 5 | `StaticResourceSearcher` | `ResourcePatternResolver` |
| 6 | `BidWinningMailSender` | `JavaMailSender`、`@Value` |
| 7 | `BidWinningTemplateFiller` | `SpringTemplateEngine` |
| 8 | `MailLoggingService` | 无 |
| 9 | `HelloController` | `HelloService` |
| 10 | `PlainTextMailController` | `PlainTextMailSender` |
| 11 | `BidWinningMailController` | `BidWinningTemplateFiller`、`BidWinningMailSender`、`MailLoggingService` |

`@Autowired` 注入发生在 Bean 创建时，Spring 通过 **AutowiredAnnotationBeanPostProcessor**（一个 `InstantiationAwareBeanPostProcessorAdapter`）实现：

```
Bean 创建 → 检查字段/构造函数上的 @Autowired → 从容器中查找匹配类型的 Bean → 通过反射（Field.set()）注入
```

---

## 2. 实体类（DTO）

### 2.1 `PlainTextMailRequest`

**文件**：`src/main/java/.../dto/PlainTextMailRequest.java`

**Lombok 注解**：

| 注解 | 生成的代码 |
|------|-----------|
| `@Data` | `getTo()`、`setTo()`、`getFrom()`、`setFrom()`、`getSubject()`、`setSubject()`、`getContent()`、`setContent()`、`equals()`、`hashCode()`、`toString()` |
| `@NoArgsConstructor` | `PlainTextMailRequest()` 无参构造器 |
| `@AllArgsConstructor` | `PlainTextMailRequest(to, from, subject, content)` 全参构造器 |

**字段**：

| 字段 | 类型 | 作用 |
|------|------|------|
| `to` | String | 收件人邮箱地址 |
| `from` | String | 发件人邮箱地址（**当前未实际使用**，发件人固定为配置中的 `spring.mail.username`） |
| `subject` | String | 邮件主题 |
| `content` | String | 邮件内容（纯文本，`PlainTextMailSender` 使用） |

**设计意图**：通用邮件数据传输对象，可同时作为请求对象、响应对象或跨层传递对象。本项目用于 `PlainTextMailSender.sendEmail()` 的参数载体。

---

### 2.2 `BidWinningMailRequest`

**文件**：`src/main/java/.../dto/BidWinningMailRequest.java`

```java
public class BidWinningMailRequest extends PlainTextMailRequest {
    private String projectCode;                        // 项目编号
    private String handlerName;                        // 经办人
    private List<BidWinningListItem> bidWinningList;   // 中标结果列表
    private String templateName;                        // 模板文件名（不含 .html 后缀）

    @Data
    public static class BidWinningListItem {
        private String supplierName;    // 供应商名称
        private String bidWinningAmount; // 中标价
    }
}
```

**继承关系**：`BidWinningMailRequest` extends `PlainTextMailRequest`

**继承来的字段**：`to`、`from`、`subject`、`content`（content 在本流程中不使用，由 HTML 模板替代）

**自有字段**：

| 字段 | 类型 | 作用 |
|------|------|------|
| `projectCode` | String | 项目编号，用于填充中标通知书模板中的 `th:text="${projectCode}"` |
| `handlerName` | String | 经办人姓名，用于填充模板中的 `th:text="${handlerName}"` |
| `bidWinningList` | `List<BidWinningListItem>` | 中标结果列表，`th:each` 循环渲染供应商名称 + 中标价 |
| `templateName` | String | 模板文件名（不含 .html 后缀），由用户传入，后端根据此字段搜索并渲染对应模板 |

**设计意图**：
- 使用继承而非组合，是为了复用 `to`/`subject` 等字段，避免重复定义
- `BidWinningMailRequest` 专门用于中标通知书邮件场景，携带模板渲染所需的项目信息
- `bidWinningList` 支持多行中标数据，前端传入 JSON 数组，后端通过 Thymeleaf `th:each` 渲染
- `templateName` 由用户传入，实现动态模板选择；传入值必须与 `classpath:/templates/` 下的实际文件名精确匹配，不存在则抛异常

---

## 2.3 实体类（Entity）

**目录**：`src/main/java/io/gitee/lujianshi/bidwinningbackend/entity/`

**职责**：与数据库表结构一一对应的 Java POJO。当前为**纯 POJO**，不带任何 ORM 注解（待引入 MyBatis-Plus / JPA 后再加）。

**共同特点**：

| 特征 | 说明 |
|------|------|
| Lombok 注解 | `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` |
| 命名风格 | Java 驼峰，对应 DB 下划线（如 `handlerName` ↔ `handler_name`） |
| 主键类型 | `Integer`（包装类，区分 null 与 0） |
| 金额字段 | `BigDecimal`（避免浮点精度问题） |
| 布尔字段 | `Boolean`（包装类，区分 null 与 false） |
| 时间字段 | `LocalDateTime`（Java 8+，线程安全） |

### 2.3.1 `BidHandlerInfo`

**对应表**：`bid_handler_info`（V3 重命名自 `handler_info`）

| Java 字段 | DB 字段 | 类型 | 必填 |
|-----------|---------|------|------|
| `handlerId` | `handler_id` | `Integer` | ✅（PK, AUTO_INCREMENT） |
| `handlerName` | `handler_name` | `String` | ✅ |
| `companyName` | `company_name` | `String` | ✅（V2+） |
| `departmentName` | `department_name` | `String` | ✅（V2+） |

### 2.3.2 `BidWinningNotification`

**对应表**：`bid_winning_notification`（V4 创建）

19 个字段，详见 `docs/database.md` 2.2 节。

### 2.3.3 `BidWinningSupplierDetail`

**对应表**：`bid_winning_supplier_detail`（V5 创建）

| Java 字段 | DB 字段 | 类型 | 必填 |
|-----------|---------|------|------|
| `detailId` | `detail_id` | `Integer` | ✅（PK, AUTO_INCREMENT） |
| `notificationId` | `notification_id` | `Integer` | ✅（FK，关联主表） |
| `supplierName` | `supplier_name` | `String` | ✅ |
| `amount` | `amount` | `BigDecimal` | ✅ |

**与 DTO 的命名差异**（已知问题）：

| Java 字段 | DTO 字段 | DB 字段 |
|-----------|----------|---------|
| `projectNo` | `projectCode` | `project_no` |
| `amount` | `bidWinningAmount` | `amount` |

**解决思路**（待引入 ORM 后处理）：
- 方案 A：Entity 跟 DB 命名，DTO 跟业务命名，加 Mapper 转换层
- 方案 B：Entity 跟 DTO 命名，加 `@TableField("xxx")` 映射

---

## 3. Controller 层

### 3.1 `HelloController`

**文件**：`src/main/java/.../controller/HelloController.java`

**类注解**：`@RestController = @Controller + @ResponseBody`

**含义**：所有方法的返回值直接序列化写入 HTTP Response Body（不经过视图解析器渲染模板）。Spring 默认使用 **Jackson**（或 fastjson2，看配置）将返回值对象序列化为 JSON。

---

#### 3.1.1 `GET /hello`

```java
@GetMapping("/hello")
public String hello() {
    return helloService.hello();
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | GET |
| 完整 URL | `http://localhost:8079/hello` |
| 参数 | 无 |
| 调用链路 | DispatcherServlet → HandlerMapping 匹配 → `hello()` → `helloService.hello()` |
| 返回值 | 字符串 `"Hello World!"`，直接写入 response body |

---

#### 3.1.2 `GET /access-url`

```java
@GetMapping("/access-url")
public void accessUrl(HttpServletResponse response) throws IOException {
    helloService.accessUrl(response);
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | GET |
| 完整 URL | `http://localhost:8079/access-url` |
| 参数 | `HttpServletResponse`（由 Spring 自动注入，基于 `HandlerMethodArgumentResolver`） |
| 调用链路 | `helloService.accessUrl(response)` → `response.sendRedirect(url)` |
| 返回值 | void（重定向操作直接由 `HttpServletResponse` 完成） |

**技术细节**：`HttpServletResponse` 是 Servlet API 接口，Spring MVC 的 `RequestMappingHandlerAdapter` 内置了对它的支持，自动将 request 级别的对象作为参数传入。

---

#### 3.1.3 `POST /fetch-url`

```java
@PostMapping("/fetch-url")
public String fetchUrl() {
    helloService.fetchAfterDelay();   // @Async，触发后立即返回，不等待
    return helloService.fetchUrl();    // 同步，立即返回配置中的 URL
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8079/fetch-url` |
| 参数 | 无 |
| 第一步调用 | `helloService.fetchAfterDelay()` → `@Async` 异步执行，**不等待**，立即返回 |
| 第二步调用 | `helloService.fetchUrl()` → 返回 `custom.url` 配置值（`https://baidu.com`） |
| 返回值 | 字符串 `"https://baidu.com"` |

**关键点**：两次调用是**串行**执行的，但第一步是异步的（`fetchAfterDelay` 立即返回）。由于 `fetchAfterDelay` 只打印日志不返回数据，所以不影响最终返回值。

---

### 3.2 `PlainTextMailController`

**文件**：`src/main/java/.../controller/PlainTextMailController.java`

#### `POST /send-email`

```java
@PostMapping("/send-email")
public void sendEmail(PlainTextMailRequest request) {
    plainTextMailSender.sendEmail(request);
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8079/send-email` |
| 请求参数 | `PlainTextMailRequest`（form-data: `to`/`subject`/`content`） |
| 调用链路 | `PlainTextMailController.sendEmail(request)` → `PlainTextMailSender.sendEmail(request)` → `JavaMailSender.send(message)` |
| 返回值 | void |

**Spring 参数绑定流程**：

```
HTTP POST form-data
  ↓
ServletRequestDataBinder（Spring MVC 内部）
  ↓
遍历 PlainTextMailRequest 所有字段，通过 setter 注入
  ↓
例如：plainTextMailRequest.setTo(request.getParameter("to"))
  ↓
PlainTextMailRequest 对象完整构建后，传入 Controller 方法
```

---

### 3.3 `BidWinningMailController`

**文件**：`src/main/java/.../controller/BidWinningMailController.java`

#### `POST /send-notification`

```java
@PostMapping("/send-notification")
public String sendNotification(@RequestBody BidWinningMailRequest request) {
    String htmlContent = bidWinningTemplateFiller.processTemplate(request);

    AsyncTaskManager.execute(() -> {
        System.out.println(mailLoggingService.formatMailLog(
            request.getTo(), request.getSubject(), htmlContent));

        try {
            bidWinningMailSender.sendHtmlEmail(
                request.getTo(),
                request.getSubject(),
                htmlContent
            );
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }, 5000);
    return "发送成功";
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8079/send-notification` |
| 请求 Content-Type | `application/json`（`@RequestBody` 注解要求） |
| 请求参数 | `BidWinningMailRequest`（JSON body） |
| 调用链路 | 见下方详细分解 |
| 返回值 | 字符串 `"发送成功"`（**立即返回**，不等待邮件实际发送） |

**完整调用链路（模板动态选择 + 异步延迟发送）**：

```
Step 1. 接收 BidWinningMailRequest（JSON → @RequestBody 自动反序列化）
        - to / subject / projectCode / handlerName / bidWinningList / templateName

Step 2. bidWinningTemplateFiller.processTemplate(request)
        ├─ 读取 request.getTemplateName() → 例如 "bid-winning-notification2"
        ├─ staticResourceSearcher.validateTemplateExists(templateName)
        │   ├─ 扫描 classpath:/templates/*.html
        │   ├─ 精确匹配 "bid-winning-notification2"
        │   └─ 找不到则抛出 IllegalArgumentException，不 fallback
        ├─ 创建 new Context()
        ├─ context.setVariable("projectCode", request.getProjectCode())
        ├─ context.setVariable("handlerName", request.getHandlerName())
        ├─ context.setVariable("bidWinningList", request.getBidWinningList())
        └─ templateEngine.process(templateName, context)
            ├─ TemplateResolver 拼接完整路径：
            │   classpath:/templates/ + "bid-winning-notification2" + ".html"
            │   = classpath:/templates/bid-winning-notification2.html
            ├─ 读取 HTML 文件内容
            ├─ 扫描 HTML 中的 th:text="${projectCode}" 等 Thymeleaf 表达式
            ├─ th:each 循环渲染 bidWinningList 每行数据
            ├─ 用 Context 中的变量值替换表达式，生成最终 HTML 字符串
            └─ 返回渲染后的 HTML 字符串

Step 3. 立即返回 "发送成功"（主线程不受异步任务影响）

Step 4. 异步任务（AsyncTaskManager.execute(..., 5000)，延迟5秒后执行）：
        ├─ mailLoggingService.formatMailLog(to, subject, htmlContent)
        │   → 拼接 MD 格式日志字符串，System.out.println 打印到控制台
        └─ bidWinningMailSender.sendHtmlEmail(to, subject, htmlContent)
            ├─ mailSender.createMimeMessage() 创建 MimeMessage
            ├─ new MimeMessageHelper(mimeMessage, true, "UTF-8")
            ├─ helper.setFrom(senderEmail)
            ├─ helper.setTo(to)
            ├─ helper.setSubject(subject)
            ├─ helper.setText(htmlContent, true)  ← HTML 格式
            └─ mailSender.send(mimeMessage)
        └─ finally: System.out.println("邮件发送任务完成: to=...")（兜底日志，无论成功失败）
```

**Postman 请求示例（选择模板 v2）**：

```json
POST http://localhost:8079/send-notification
Content-Type: application/json

{
    "to": "xxx@outlook.com",
    "subject": "中标通知书",
    "projectCode": "WB20260325017",
    "handlerName": "刘曼曼",
    "bidWinningList": [
        { "supplierName": "xxxxxx分公司 合作供应商", "bidWinningAmount": "67,500.00 RMB" },
        { "supplierName": "xxxxx分公司 合作供应商", "bidWinningAmount": "83,000.00 RMB" }
    ],
    "templateName": "bid-winning-notification2"
}
```

**模板搜索行为**：
- 搜索范围：`classpath:/templates/*.html`（PathMatchingResourcePatternResolver 扫描）
- 匹配方式：**精确匹配**（传入 `"bid-winning-notification2"` 匹配文件名 `"bid-winning-notification2.html"`）
- 行为安全：**不存在则抛异常**，`IllegalArgumentException: 模板不存在: bid-winning-notification2`，不 fallback 到默认模板

**HTML vs 纯文本邮件的区别**：

| 对比项 | `PlainTextMailSender.sendEmail()`（纯文本） | `BidWinningMailSender.sendHtmlEmail()`（HTML） |
|--------|---------------------------------------------|------------------------------------------------|
| Spring Mail 类 | `SimpleMailMessage` | `MimeMessage` + `MimeMessageHelper` |
| 内容类型 | `text/plain` | `text/html`（第二个参数 `true`） |
| 模板支持 | 无 | Thymeleaf 渲染后填入 |
| 使用场景 | 简单通知邮件 | 包含样式、表格的正式通知书 |

---

## 4. Service 层

### 4.1 `HelloService`

**文件**：`src/main/java/.../service/HelloService.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|-------------|----------|
| `hello()` | 无 | `"Hello World!"`（String） | 无 | 最简单的业务逻辑演示 |
| `accessUrl(HttpServletResponse response)` | `HttpServletResponse` | void（通过 response 重定向） | Servlet API（`HttpServletResponse`） | 演示 Service 与 Servlet API 的耦合（不推荐，Service 应返回数据） |
| `fetchAfterDelay()` | 无 | void（异步执行） | `AsyncTaskManager` | `@Async` 注解 + `AsyncTaskManager.execute()` 双重异步 |
| `fetchUrl()` | 无 | `custom.url` 配置值（String） | `@Value` 注入配置 | 返回配置值 |

**注入的配置**：
```java
@Value("${server.port}")    private int port;       // 8079
@Value("${custom.url}")    private String url;      // https://baidu.com
```

**`@Value` 注入原理**：`AutowiredAnnotationBeanPostProcessor`（实现了 `InstantiationAwareBeanPostProcessor` 接口）在 Bean 初始化后，扫描所有带有 `@Value` 的字段，从 Spring `Environment` 中读取对应的 `application.properties` 配置值，通过反射（`Field.setAccessible(true); field.set(this, value)`）注入。

---

### 4.2 `PlainTextMailSender`

**文件**：`src/main/java/.../service/PlainTextMailSender.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|-------------|----------|
| `sendEmail(PlainTextMailRequest request)` | `PlainTextMailRequest`（包含 to/subject/content） | void（发送纯文本邮件） | `JavaMailSender`、`SimpleMailMessage`、`senderEmail`（配置值） | 纯文本邮件发送，强制使用配置中的发件人 |

**`sendEmail()` 内部流程**：

```java
public void sendEmail(PlainTextMailRequest request) {
    SimpleMailMessage message = new SimpleMailMessage();    // 1. 创建邮件消息对象
    message.setFrom(senderEmail);                            // 2. 强制使用配置发件人（防止 from 欺骗）
    message.setTo(request.getTo());                           // 3. 收件人
    message.setSubject(request.getSubject());                // 4. 主题
    message.setText(request.getContent());                   // 5. 纯文本内容
    mailSender.send(message);                                // 6. 通过 SMTP 发送
}
```

**关键设计决策**：发件人固定使用 `senderEmail`（即 `spring.mail.username`），而非 `request.getFrom()`。原因：
1. 安全：防止发件人地址被伪造（与 SMTP 服务器授权用户不符会报错）
2. 统一：所有发出的邮件都从一个发件地址发出

**`JavaMailSender` 发送流程（底层原理）**：

```
mailSender.send(message)
  ↓
JavaMailSenderImpl.send(SimpleMailMessage)
  ↓
JavaMailSenderImpl.createMimeMessage()  ← 实际上 SimpleMailMessage 内部也创建 MimeMessage
  ↓
Session.getInstance(props, auth)         ← 使用配置的 SMTP 属性（host/port/username/password）
  ↓
Transport.send(message)                  ← 通过 TCP socket 连接 smtp.qq.com:587
     ↓
     smtp.qq.com STARTTLS               ← 加密传输
     ↓
     AUTH LOGIN                          ← 使用 username/password 认证
     ↓
     MAIL FROM: <1649681371@qq.com>      ← 必须与认证用户一致
     ↓
     RCPT TO: <request.getTo()>          ← 收件人地址
     ↓
     DATA                                ← 邮件内容
     ↓
     QUIT
```

---

### 4.3 `StaticResourceSearcher`

**文件**：`src/main/java/.../service/StaticResourceSearcher.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|-------------|----------|
| `validateTemplateExists(String templateName)` | templateName（模板文件名，不含 .html 后缀） | void（验证通过则正常返回，失败则抛 `IllegalArgumentException`） | `ResourcePatternResolver`（Spring Core） | 精确搜索 `classpath:/templates/` 目录验证模板文件是否存在，为 `BidWinningTemplateFiller` 提供模板存在性校验，不存在则抛异常（不 fallback） |

**`validateTemplateExists()` 内部流程**：

```java
public void validateTemplateExists(String templateName) {
    try {
        // 1. 扫描 classpath:/templates/*.html 下的所有模板文件
        Resource[] resources = resourcePatternResolver.getResources("classpath:/templates/*.html");
        //   ↓
        //   PathMatchingResourcePatternResolver.getResources("classpath:/templates/*.html")
        //     ↓
        //     ClassLoader.getResources("templates/")  ← 遍历 classpath 下所有 templates 目录
        //     ↓
        //     对每个 .html 文件创建 ClassPathResource 对象
        //     ↓
        //     返回 Resource[] 数组

        // 2. 精确匹配
        boolean exists = Arrays.stream(resources)
                .map(this::extractTemplateName)              // 提取文件名（去 .html 后缀）
                .anyMatch(name -> name != null && name.equals(templateName));
                // 精确匹配：传入 "bid-winning-notification2"
                // → 只匹配文件名完全等于 "bid-winning-notification2" 的文件

        // 3. 找不到则抛异常，不 fallback
        if (!exists) {
            throw new IllegalArgumentException("模板不存在: " + templateName);
        }
    } catch (IOException e) {
        throw new IllegalArgumentException("模板搜索失败: " + templateName, e);
    } finally {
        System.out.println("模板搜索完成: " + templateName);
    }
}
```

**`extractTemplateName()` 内部流程**：

```java
private String extractTemplateName(Resource resource) {
    try {
        String path = resource.getURL().getPath();
        // resource.getURL() 返回 java.net.URL
        // .getPath() 返回完整系统路径，如 /Users/.../target/classes/templates/bid-winning-notification2.html
        int idx = path.lastIndexOf("/templates/");
        if (idx < 0) return null;
        return path.substring(idx + "/templates/".length()).replace(".html", "");
        // /templates/bid-winning-notification2.html → "bid-winning-notification2"
    } catch (IOException e) {
        return null;
    }
}
```

**设计意图**：
- 使用 `ResourcePatternResolver` 而不是直接读取文件系统，确保在打包成 JAR 后仍能正确扫描
- **精确匹配**：不支持通配符或模糊匹配，必须文件名完全一致
- **行为安全**：模板不存在时直接抛 `IllegalArgumentException`，不 fallback 到默认模板，保证用户传入的模板码始终受控

---

### 4.4 `BidWinningMailSender`

**文件**：`src/main/java/.../service/BidWinningMailSender.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|-------------|----------|
| `sendHtmlEmail(String to, String subject, String htmlContent)` | to（收件人）、subject（主题）、htmlContent（HTML内容） | void（发送 HTML 邮件） | `JavaMailSender`、`MimeMessage`、`MimeMessageHelper`、`senderEmail`（配置值） | 支持 HTML 格式的邮件发送，使用 MimeMessageHelper 简化构造，强制使用配置发件人 |

**`sendHtmlEmail()` 内部流程**：

```java
public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
    MimeMessage mimeMessage = mailSender.createMimeMessage();
    // 1. mailSender（JavaMailSenderImpl）通过 Session 创建 MimeMessage
    //    MimeMessage 是 jakarta.mail 中表示 MIME 邮件消息的类

    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
    // 2. MimeMessageHelper 是 Spring 封装的 MIME 邮件构建工具
    //    参数1：mimeMessage → 关联的 MIME 消息对象
    //    参数2：true → 启用 multipart（多部分），支持附件
    //    参数3："UTF-8" → 字符编码

    helper.setFrom(senderEmail);                    // 3. 发件人（强制配置值）
    helper.setTo(to);                               // 4. 收件人
    helper.setSubject(subject);                     // 5. 主题
    helper.setText(htmlContent, true);              // 6. 第二个参数 true = HTML 格式
    //    helper.setText(text, false) = 纯文本
    //    helper.setText(text, true)  = HTML（Content-Type: text/html）

    mailSender.send(mimeMessage);                   // 7. 发送 MIME 邮件
    //    JavaMailSenderImpl.send(MimeMessage) → Transport.send()
}
```

**为什么需要 `MimeMessage` + `MimeMessageHelper` 而不是 `SimpleMailMessage`？**

| 对比项 | `SimpleMailMessage` | `MimeMessage` + `MimeMessageHelper` |
|--------|---------------------|-------------------------------------|
| 内容类型 | 纯文本 | 可文本可HTML（`setText(text, true)`） |
| 附件支持 | 无 | 支持（multipart） |
| 内联图片 | 无 | 支持 |
| 多语言/编码 | 弱 | 强（可设置 charset） |
| 使用场景 | 简单邮件 | 正式 HTML 邮件（中标通知书含表格样式） |

---

### 4.5 `BidWinningTemplateFiller`

**文件**：`src/main/java/.../service/BidWinningTemplateFiller.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|-------------|----------|
| `processTemplate(BidWinningMailRequest request)` | `BidWinningMailRequest`（含 projectCode + handlerName + bidWinningList + **templateName**） | 渲染后的 HTML 字符串（String） | `SpringTemplateEngine`、`Context`、`StaticResourceSearcher` | 将 Thymeleaf 模板与动态数据结合，根据 `request.templateName` 动态选择模板文件，模板不存在则抛异常（不 fallback） |

**`processTemplate()` 内部流程**：

```java
public String processTemplate(BidWinningMailRequest request) {
    // Step 1: 读取用户传入的模板文件名（不含 .html 后缀）
    String templateName = request.getTemplateName();  // 例如 "bid-winning-notification2"

    // Step 2: 验证模板是否存在（精确匹配 classpath:/templates/*.html）
    staticResourceSearcher.validateTemplateExists(templateName);
    //    验证通过 → 继续
    //    验证失败 → 抛出 IllegalArgumentException，不 fallback

    // Step 3: 创建 Thymeleaf 上下文（数据容器）
    Context context = new Context();
    //    Context(org.thymeleaf.context) 内部维护一个 Map<String, Object>
    //    模板引擎渲染时，通过 ${变量名} 从这个 Map 中取值

    // Step 4: 放入模板变量
    context.setVariable("projectCode", request.getProjectCode());
    context.setVariable("handlerName", request.getHandlerName());
    context.setVariable("bidWinningList", request.getBidWinningList());
    //    模板中 ${projectCode} → 从 Map 取到 request.getProjectCode()
    //    模板中 th:each="item : ${bidWinningList}" → 循环渲染每行中标数据

    // Step 5: 渲染模板
    return templateEngine.process(templateName, context);
    //    process(模板名, Context) 是 SpringTemplateEngine 的核心渲染方法
    //    模板名直接使用用户传入的 templateName（不含 .html），由 TemplateResolver 拼接完整路径：
    //    "bid-winning-notification2" → classpath:/templates/bid-winning-notification2.html
    //    ↓
    //    读取 HTML 文本 → 解析 Thymeleaf 表达式 → 替换变量 → th:each 循环渲染 → 返回最终 HTML 字符串
}
```

**模板搜索与选择机制（完整链路）**：

```
用户传入 templateName: "bid-winning-notification2"
        ↓
StaticResourceSearcher.validateTemplateExists("bid-winning-notification2")
        ↓
扫描 classpath:/templates/*.html → 精确匹配文件名
        ↓
找到 → TemplateResolver 拼接路径 → classpath:/templates/bid-winning-notification2.html
        ↓
读取文件 → Thymeleaf AST 解析 → 渲染
        ↓
找不到 → 抛出 IllegalArgumentException（不 fallback，不使用默认模板）
```

**Thymeleaf 渲染原理（底层）**：

```
templateEngine.process(templateName, context)
  ↓
TemplateResolver.resolveTemplateName(templateName)
  → 拼接完整路径 classpath:/templates/{templateName}.html
  ↓
TemplateParser.parseTemplate()  ← 解析 HTML 为 AST（抽象语法树）
  → 遍历 AST 节点，遇到 th:text="${projectCode}" 节点
  → 遍历 AST 节点，遇到 th:each="item : ${bidWinningList}" 节点
  ↓
Dialect.execute()  ← 执行 OGNL/SpringEL 表达式从 Context 取值
  → context.getVariable("projectCode") → "WB20260325017"
  → bidWinningList 循环渲染每项 supplierName + bidWinningAmount
  ↓
OutputWriter.write(result)  ← 将替换后的 HTML 写入 String
  → 返回完整 HTML 字符串
```

**设计决策**：模板名由用户传入，实现动态模板选择；验证后不使用默认模板，保证用户传入的 `templateName` 始终受控。

**`SpringTemplateEngine` 的 Bean 来源**：`ThymeleafAutoConfiguration` 在容器启动时注册，单例模式。

---

### 4.6 `MailLoggingService`

**文件**：`src/main/java/.../service/MailLoggingService.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|-------------|----------|
| `formatMailLog(String to, String subject, String htmlContent)` | to（收件人）、subject（主题）、htmlContent（HTML内容） | MD 格式字符串（String） | `io.github.furstenheim:copy_down` | 将邮件字段拼接为 Markdown 格式（HTML 经 copy_down 转换后），供异步任务打印到控制台，便于后续扩展（文件/数据库等） |

**`formatMailLog()` 输出格式**（copy_down 将 HTML 表格转换为 Markdown 表格）：

```markdown
## 邮件发送日志
**收件人:** xxx@outlook.com
**主题:** 中标通知书
| 供应商名称 | 中标价 |
|---|---|
| xxx公司 | 67,500.00 RMB |
```

**用途**：`BidWinningMailController` 在异步任务中调用 `System.out.println()` 打印到控制台。设计为独立方法，方便后续扩展日志输出目标（文件、数据库、消息队列等）。

---

## 5. 工具类

### 5.1 `AsyncTaskManager`

**文件**：`src/main/java/.../utils/AsyncTaskManager.java`

| 方法 | 输入 | 输出 | 线程模型 | 设计思路 |
|------|------|------|----------|----------|
| `execute(Runnable task, long delayMillis)` | `task`（任务逻辑）、`delayMillis`（延迟时间） | void（立即返回） | 每次调用创建 1 个新线程 | 轻量级异步工具，非 Spring Bean，Thread + Runnable 模式 |

**`execute()` 内部流程**：

```java
public static void execute(Runnable task, long delayMillis) {
    new Thread(() -> {                              // 1. 创建新线程（Lambda 实现 Runnable）
        try {
            Thread.sleep(delayMillis);               // 2. 子线程睡眠 delayMillis 毫秒（不阻塞主线程）
            task.run();                               // 3. 睡眠结束后执行 task.run()（业务逻辑）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();      // 4. InterruptedException 处理
            throw new RuntimeException(e);            //   - 恢复中断状态
        }                                            //   - 转换为未检查异常上抛
        } finally {
            System.out.println("异步任务执行完成: delayMillis=" + delayMillis);
        }                                            // 5. finally 兜底日志（无论成功失败都执行）
    }).start();                                      // 6. 启动线程，立即返回（不等待子线程执行）
}
```

**为什么 `start()` 后立即返回？**

`Thread.start()` 的语义是"启动线程并立即返回"，JVM 会异步调度新线程执行 `run()` 方法。调用方主线程不受影响，继续执行后续代码。

**`InterruptedException` 处理说明**：

```
主线程调用 execute() 后继续执行
    ↓
new Thread 子线程执行 Thread.sleep(delayMillis)
    ↓
如果主线程调用 Thread.interrupt() 或线程被中断
    ↓
Thread.sleep() 抛出 InterruptedException
    ↓
catch 块：Thread.currentThread().interrupt()
    → 将线程的中断标志设为 true（JVM 规范要求的正确处理方式）
    → throw new RuntimeException(e)
    → 上抛异常通知调用方
```

**与 `@Async` 的区别**：

| 对比项 | `AsyncTaskManager.execute()` | `@Async` |
|--------|-----------------------------| --------|
| 管理方式 | 每次调用创建新线程 | 委托给线程池（`SimpleAsyncTaskExecutor`） |
| 延迟执行 | 支持（通过 `Thread.sleep()`） | 不支持延迟，需要手动 sleep |
| Spring 集成 | 无（纯 Java 标准库） | 需要 `@EnableAsync` + Spring AOP 代理 |
| 线程复用 | 无（每次新线程，有创建开销） | 线程池复用 |
| 是否阻塞调用方 | 否（立即返回） | 否（立即返回） |

**生产环境建议**：高频调用时改用 `ExecutorService` 线程池，避免大量线程创建开销。

---

## 6. 配置与模板

### 6.1 多环境配置（application.yml 系列）

**文件结构**：

```
src/main/resources/
├── application.yml       # 公共配置 + 默认 profile = dev
├── application-dev.yml   # 开发环境
├── application-test.yml  # 测试环境
└── application-prod.yml  # 生产环境
```

**公共配置**（`application.yml`）：

```yaml
spring:
  application:
    name: bid-winning-backend
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
  profiles:
    active: dev   # 默认启动 dev 环境
```

#### 6.1.1 各环境配置

| Profile | 端口 | 日志路径 | 备注 |
|---------|------|----------|------|
| `dev`（开发） | `8079` | `logs/app.log` | 默认环境，明文密码，QQ SMTP |
| `test`（测试） | `8080` | `logs/test.log` | 日志级别 DEBUG，密码用 `${TEST_MAIL_PASSWORD:default}` 占位符 |
| `prod`（生产） | `80` | `logs/prod.log` | 日志级别 INFO，敏感信息全部走环境变量（`${PROD_MAIL_PASSWORD}`） |

#### 6.1.2 配置示例（dev）

```yaml
server:
  address: 0.0.0.0
  port: 8079

custom:
  url: https://baidu.com

spring:
  mail:
    host: smtp.qq.com
    port: 587
    username: 1649681371@qq.com
    password: xhixpbmbkfyhchbb
    default-encoding: UTF-8
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true

logging:
  file:
    name: logs/app.log
```

#### 6.1.3 环境切换方式

**方式 1：默认 profile（推荐）**
修改 `application.yml` 中的 `spring.profiles.active` 切换。

**方式 2：启动时指定**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

**方式 3：环境变量**
```bash
export SPRING_PROFILES_ACTIVE=prod
./mvnw spring-boot:run
```

#### 6.1.4 关键配置项对照

| 配置项 | dev | test | prod |
|--------|-----|------|------|
| `server.port` | `8079` | `8080` | `80` |
| `spring.mail.username` | `1649681371@qq.com` | `${TEST_MAIL_USERNAME:...}` | `${PROD_MAIL_USERNAME}` |
| `spring.mail.password` | `xhixpbmbkfyhchbb`（明文） | `${TEST_MAIL_PASSWORD:...}`（占位符） | `${PROD_MAIL_PASSWORD}`（环境变量，强制） |
| `custom.url` | `https://baidu.com` | `https://test.example.com` | `https://prod.example.com` |
| `logging.level.root` | `INFO`（默认） | `DEBUG` | `INFO` |

**关于 SMTP 授权码**：授权码是 QQ 邮箱为第三方客户端提供的专用密码，不能用 QQ 登录密码代替。授权码在 QQ 邮箱设置 → 账户 → POP3/SMTP服务 中生成。

**安全建议**：生产环境 `application-prod.yml` 应加入 `.gitignore`，或提交到独立私有仓库，敏感信息通过环境变量/CI 注入。

---

### 6.2 `pom.xml` 关键依赖

**文件**：`pom.xml`

| 依赖 | 版本 | 传递引入的核心类库 | 作用 |
|------|------|-------------------|------|
| `spring-boot-starter-webmvc` | 由 parent 管理 | spring-webmvc + spring-context + tomcat-embed | Web 框架 + Servlet 容器 |
| `spring-boot-starter-mail` | 由 parent 管理 | spring-context-support + jakarta.mail | 邮件发送支持 |
| `spring-boot-starter-thymeleaf` | 由 parent 管理 | spring-boot-starter-thymeleaf + thymeleaf-spring6 | 模板引擎 |
| `fastjson2` | 2.0.52 | 阿里 JSON 序列化库 | JSON 处理（额外引入，本项目未直接使用） |
| `lombok` | 由 parent 管理 | 无（编译时注解处理） | 自动生成 getter/setter |
| `copy_down` | 1.1 | io.github.furstenheim:copy_down | HTML → Markdown 转换（MailLoggingService 使用） |

**依赖传递链**：

```
spring-boot-starter-mail
  ├─ spring-boot-starter → spring-boot → spring-boot-autoconfigure
  ├─ spring-context-support → JavaMailSender
  └─ jakarta.mail-api → MimeMessage / MimeMessageHelper

spring-boot-starter-webmvc
  ├─ spring-webmvc → DispatcherServlet / HandlerMapping / HandlerAdapter
  ├─ spring-context → BeanFactory / ApplicationContext / @Autowired
  └─ tomcat-embed-core → 嵌入式 Tomcat
```

---

### 6.3 `bid-winning-notification.html` 模板

**文件**：`src/main/resources/templates/bid-winning-notification.html`

**Thymeleaf 变量**：

| 模板变量 | 数据来源 | 占位写法 | 最终效果 |
|---------|----------|----------|----------|
| `${projectCode}` | `BidWinningMailRequest.projectCode` | `<span th:text="${projectCode}">000001</span>` | 渲染后显示具体项目编号 |
| `${handlerName}` | `BidWinningMailRequest.handlerName` | `<span th:text="${handlerName}">张三</span>` | 渲染后显示具体经办人 |

**设计决策**：使用 `<span th:text="${var}">默认值</span>` 而不是 `<input th:value="${var}">`：
1. `<input>` 元素在 Outlook 等邮件客户端中渲染效果差
2. `<span>` 是行内元素，渲染自然
3. `th:text` 覆盖标签内文本内容，标签本身不会显示

**为什么使用 `<table>` 布局**：HTML 邮件客户端（Outlook、Apple Mail 等）对 CSS float/flexbox 支持不一致，使用 `<table>` 做布局是最稳妥的兼容性方案。

---

## 附录：关键术语表

| 术语 | 全称 | 解释 |
|------|------|------|
| IOC | Inversion of Control | 控制反转，Spring 容器负责 Bean 的创建和依赖注入 |
| AOP | Aspect-Oriented Programming | 面向切面编程，@Async 基于 AOP 代理实现 |
| Servlet | Java Servlet API | Java Web 标准接口，处理 HTTP 请求 |
| MIME | Multipurpose Internet Mail Extensions | 互联网邮件扩展格式，支持 HTML 邮件 |
| Thymeleaf | Thymeleaf Template Engine | Spring Boot 默认模板引擎 |
| STARTTLS | STARTTLS SMTP Extension | SMTP 命令，用于在明文连接上升级为 TLS 加密 |
| DTO | Data Transfer Object | 数据传输对象，用于层间数据传递 |
| OGNL | Object-Graph Navigation Language | 对象图导航语言，Thymeleaf 表达式求值引擎之一 |
| Bean Post Processor | BeanPostProcessor | Spring 框架钩子，在 Bean 创建/销毁前后执行拦截 |
| DispatcherServlet | DispatcherServlet | Spring MVC 前端控制器，所有 HTTP 请求的入口 |
| HandlerMapping | HandlerMapping | URL 到 Controller 方法的映射注册表 |
| HandlerAdapter | HandlerAdapter | 调用 Controller 方法并处理返回值的适配器 |
