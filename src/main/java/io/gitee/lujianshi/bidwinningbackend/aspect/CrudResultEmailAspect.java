package io.gitee.lujianshi.bidwinningbackend.aspect;

import com.alibaba.fastjson2.JSON;
import io.gitee.lujianshi.bidwinningbackend.annotation.SendEmailOnSuccess;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.service.mail.BidWinningMailSender;
import io.gitee.lujianshi.bidwinningbackend.service.mail.StaticResourceSearcher;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import jakarta.annotation.Resource;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CRUD 操作成功后发送邮件通知切面
 *
 * <h3>为什么需要这个切面（横切关注点 Cross-cutting Concerns）</h3>
 * <ul>
 *   <li>邮件通知逻辑如果写在每个 Controller，18 个端点会有 18 段重复代码</li>
 *   <li>用 AOP 切面统一处理：Controller 业务代码 0 改动，只需贴一个 {@link SendEmailOnSuccess} 注解</li>
 *   <li>典型横切场景：日志、鉴权、缓存、通知、限流、脱敏</li>
 * </ul>
 *
 * <h3>拦截规则（Spring AOP @AfterReturning 语义）</h3>
 * <ul>
 *   <li>拦截所有标注了 {@link SendEmailOnSuccess} 的方法（{@code pointcut = "@annotation(anno)"}）</li>
 *   <li>仅当方法<b>正常返回</b>时触发（异常不触发，{@code @AfterReturning} 的标准语义）</li>
 *   <li>如果需要异常时也触发，用 {@code @AfterThrowing}；如果需要完全控制，用 {@code @Around}</li>
 * </ul>
 *
 * <h3>完整执行链路（5 步传参与验证 + 4 步异步发送）</h3>
 * <pre>
 * Spring 代理对象（"秘书"）调用 {@code handleAfterReturning} 方法，传入 3 个参数：
 *
 * ┌─ 参数 1: JoinPoint joinPoint ─────────────────────────────┐
 * │   MethodSignature signature = (MethodSignature) joinPoint │
 * │   Method method = signature.getMethod()                │
 * │     → method.getDeclaringClass() → 类名                    │
 * │     → method.getName()         → 方法名                    │
 * │   joinPoint.getSignature()    → 完整方法签名（含声明类）    │
 * └──────────────────────────────────────────────────────────┘
 * ┌─ 参数 2: Object result (业务返回值) ─────────────────────┐
 * │   instanceof Result<?> 验证（防御性编程，类型安全）         │
 * │   提取 r.getCode() / getMessage() / getData()             │
 * └──────────────────────────────────────────────────────────┘
 * ┌─ 参数 3: SendEmailOnSuccess anno (注解实例) ─────────────┐
 * │   anno.to()          → 收件人邮箱                          │
 * │   anno.subject()     → 邮件主题（默认空 → 切面自动构造）    │
 * │   anno.templateName() → Thymeleaf 模板名（默认 crud-result）│
 * └──────────────────────────────────────────────────────────┘
 *
 * 切面方法内部（主线程，同步执行）：
 *   ① 验证 result instanceof Result<?> （不强类型依赖）
 *   ② 反射推断 endpoint（HTTP method + 类名 + 方法名）
 *   ③ 推断 operation（CREATE / UPDATE / DELETE / BATCH_DELETE / QUERY）
 *   ④ fastjson2 序列化 Result.data 为 JSON 字符串
 *   ⑤ 构造 subject（注解覆盖 or 默认格式）
 *   ⑥ AsyncTaskManager.execute(...) 提交异步任务 → 主线程立即返回
 *
 * 异步线程池（工作线程，延迟 0 ms 执行）：
 *   ⑦ StaticResourceSearcher.validateTemplateExists(...) → 模板存在性校验
 *   ⑧ 构造 Thymeleaf Context，注入 subject/endpoint/operation/timestamp/result*
 *   ⑨ SpringTemplateEngine.process(templateName, context) → 渲染 HTML
 *   ⑩ BidWinningMailSender.sendHtmlEmail(to, subject, html) → SMTP 发送
 *   ⑪ try-catch 兜底（邮件失败仅日志告警，不影响主业务）
 * </pre>
 *
 * <h3>为什么用 fastjson2 而非 Jackson</h3>
 * <ul>
 *   <li>项目已引入 {@code com.alibaba.fastjson2:fastjson2:2.0.52}，复用避免新增依赖</li>
 *   <li>{@code JSON.toJSONString()} 是 fastjson2 的 API</li>
 * </ul>
 *
 * <h3>为什么用 Java 反射（MethodSignature + Method）</h3>
 * <ul>
 *   <li>切面不知道具体拦截哪个方法 → 用反射动态获取方法元信息</li>
 *   <li>{@code MethodSignature} 是 AspectJ 提供的类型，包装了 {@link Method} + 泛型解析</li>
 *   <li>如果注解写在类上（{@code @Target(ElementType.TYPE)}），可以用 {@code joinPoint.getTarget()} 获取类</li>
 * </ul>
 *
 * <h3>为什么必须 try-catch 兜底（防御性编程）</h3>
 * <ul>
 *   <li>切面代码<b>绝对不能</b>因为自身报错导致主业务方法回滚或请求失败</li>
 *   <li>邮件发失败就失败日志告警，不能让用户的招投标信息保存失败</li>
 *   <li>整个 {@code handleAfterReturning} 被大大的 try-catch 包裹（外层）</li>
 *   <li>异步任务内部也独立 try-catch（内层）</li>
 * </ul>
 *
 * <h3>为什么异步发送（{@code AsyncTaskManager.execute}）</h3>
 * <ul>
 *   <li>发邮件涉及 SMTP 网络 I/O，耗时可达 1-3 秒</li>
 *   <li>同步发送会拖慢接口响应时间，用户体验差</li>
 *   <li>异步后：HTTP 响应立即返回（200 OK），邮件在后台慢慢发</li>
 *   <li>{@code AsyncTaskManager} 是项目自实现的轻量级异步工具（{@code new Thread(...).start()}）</li>
 *   <li>生产环境建议改用线程池（{@code ExecutorService}）复用线程</li>
 * </ul>
 *
 * <h3>注意：代理失效场景（防坑）</h3>
 * <ul>
 *   <li>只有通过 Spring 注入的对象（依赖注入）调用其方法时，AOP 才会生效</li>
 *   <li>类<b>内部</b>的方法 A 直接调用方法 B，B 上的注解<b>不会</b>触发切面（跳过了"秘书"，老板自己干了）</li>
 *   <li>解决方案：注入自己的代理（{@code @Resource private CrudResultEmailAspect self;}），通过 {@code self.methodB()} 调用</li>
 * </ul>
 *
 * <h3>为什么注解实例能绑定到方法的 {@code anno} 参数</h3>
 * <ul>
 *   <li>Spring AOP 通过反射读取方法上的注解 → 创建注解实例</li>
 *   <li>{@code @AfterReturning} 的 {@code argNames} 参数指定形参名（编译期字节码保留）</li>
 *   <li>形参 {@code SendEmailOnSuccess anno} 直接接收注解实例，无需手动反射</li>
 * </ul>
 *
 * <h3>技术栈协同</h3>
 * <ul>
 *   <li><b>Spring AOP / AspectJ</b>：横向拦截（{@code @Aspect} + {@code @AfterReturning}）</li>
 *   <li><b>Java 反射</b>：动态获取方法元信息（{@code MethodSignature}）</li>
 *   <li><b>fastjson2</b>：实体转 JSON 字符串（{@code JSON.toJSONString}）</li>
 *   <li><b>Thymeleaf</b>：模板引擎渲染 HTML（{@code SpringTemplateEngine}）</li>
 *   <li><b>AsyncTaskManager</b>：异步线程池（{@code AsyncTaskManager.execute}）</li>
 *   <li><b>JavaMailSender</b>：SMTP 协议发邮件（{@code BidWinningMailSender.sendHtmlEmail}）</li>
 * </ul>
 *
 * <h3>扩展点（其他横切场景可参考本类）</h3>
 * <ul>
 *   <li>操作日志记录切面（{@code @Before} + MethodSignature）</li>
 *   <li>接口限流切面（{@code @Around} + Redis）</li>
 *   <li>敏感数据脱敏切面（{@code @AfterReturning} + 处理 Result.data）</li>
 *   <li>分布式锁切面（{@code @Around}）</li>
 * </ul>
 */
@Aspect
@Component
public class CrudResultEmailAspect {

    @Resource private SpringTemplateEngine templateEngine;
    @Resource private StaticResourceSearcher staticResourceSearcher;
    @Resource private BidWinningMailSender bidWinningMailSender;

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 拦截带 {@link SendEmailOnSuccess} 注解的方法，在正常返回后执行
     *
     * <h3>参数绑定机制（Spring AOP 自动注入）</h3>
     * <ul>
     *   <li>{@code JoinPoint joinPoint}：当前执行方法的连接点信息（Spring AOP 自动注入）</li>
     *   <li>{@code Object result}：被拦截方法的返回值（{@code returning = "result"} 绑定）</li>
     *   <li>{@code SendEmailOnSuccess anno}：被拦截方法上的注解实例（{@code argNames} 绑定）</li>
     * </ul>
     *
     * <h3>执行时机（{@code @AfterReturning} 语义）</h3>
     * <ul>
     *   <li>仅当目标方法<b>正常返回</b>时触发（无异常）</li>
     *   <li>返回值已生成但还未返回给调用方时执行</li>
     *   <li>异常情况下不执行（异常由 {@code @AfterThrowing} 处理）</li>
     * </ul>
     *
     * <h3>5 步传参与验证（同步，主线程）</h3>
     * <ol>
     *   <li>{@code instanceof Result<?>} 验证（防御性编程）</li>
     *   <li>反射提取 method 签名，推断 endpoint/operation</li>
     *   <li>fastjson2 序列化 Result.data 为 JSON</li>
     *   <li>构造邮件主题（注解覆盖 or 默认格式）</li>
     *   <li>{@code AsyncTaskManager.execute(...)} 提交异步任务</li>
     * </ol>
     *
     * <h3>4 步异步发送（工作线程，延迟 0 ms）</h3>
     * <ol>
     *   <li>{@code StaticResourceSearcher.validateTemplateExists()} 模板存在性校验</li>
     *   <li>构造 Thymeleaf Context 注入变量</li>
     *   <li>{@code SpringTemplateEngine.process()} 渲染 HTML</li>
     *   <li>{@code BidWinningMailSender.sendHtmlEmail()} SMTP 发送</li>
     * </ol>
     */
    @AfterReturning(
            pointcut = "@annotation(anno)",
            returning = "result",
            argNames = "joinPoint, result, anno"
    )
    public void handleAfterReturning(JoinPoint joinPoint, Object result,
                                     SendEmailOnSuccess anno) {
        try {
            // 1. 验证返回值类型（防御性编程，弱化强类型依赖）
            if (!(result instanceof Result<?>)) {
                System.err.println("[CRUD-EMAIL] 返回值非 Result 类型，跳过: "
                        + joinPoint.getSignature());
                return;
            }
            Result<?> r = (Result<?>) result;

            // 2. 反射提取方法元信息（推断 endpoint/operation）
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String endpoint = getEndpoint(method);
            String operation = inferOperation(endpoint);
            // 3. 序列化 Result.data 为 JSON 字符串（fastjson2）
            String resultDataJson = JSON.toJSONString(r.getData());

            // 4. 构造邮件主题（注解覆盖 or 默认格式）
            String subject = anno.subject().isEmpty()
                    ? String.format("[业务操作通知] %s %s - %s",
                            endpoint, operation,
                            LocalDateTime.now().format(TS_FMT))
                    : anno.subject();

            // 5. 提交异步任务（主线程立即返回，不阻塞业务响应）
            final String to = anno.to();
            final String templateName = anno.templateName();
            final String finalSubject = subject;
            final String finalOperation = operation;
            final String finalEndpoint = endpoint;
            final String finalResultCode = String.valueOf(r.getCode());
            final String finalResultMessage = r.getMessage();
            final String finalResultDataJson = resultDataJson;

            AsyncTaskManager.execute(() -> {
                // 内层 try-catch 兜底（异步任务内独立保护）
                try {
                    // 5.1 模板存在性校验
                    staticResourceSearcher.validateTemplateExists(templateName);

                    // 5.2 构造 Thymeleaf Context（注入邮件模板变量）
                    Context context = new Context();
                    context.setVariable("subject", finalSubject);
                    context.setVariable("endpoint", finalEndpoint);
                    context.setVariable("operation", finalOperation);
                    context.setVariable("timestamp",
                            LocalDateTime.now().format(TS_FMT));
                    context.setVariable("resultCode", finalResultCode);
                    context.setVariable("resultMessage", finalResultMessage);
                    context.setVariable("resultDataJson", finalResultDataJson);

                    // 5.3 渲染 HTML 模板
                    String htmlContent = templateEngine.process(templateName, context);
                    // 5.4 SMTP 发送邮件
                    bidWinningMailSender.sendHtmlEmail(to, finalSubject, htmlContent);
                    System.out.println("[CRUD-EMAIL] 发送成功: to=" + to);
                } catch (Exception e) {
                    System.err.println("[CRUD-EMAIL] 发送失败: to=" + to
                            + ", error=" + e.getMessage());
                }
            }, 3000);

        } catch (Exception e) {
            // 外层 try-catch 兜底：通知链路任何异常都不影响业务响应
            System.err.println("[CRUD-EMAIL] 通知链路异常: " + e.getMessage());
        }
    }

    private String getEndpoint(Method method) {
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String httpMethod = "GET";
        if (methodName.startsWith("create") || methodName.startsWith("batch")) {
            httpMethod = "POST";
        } else if (methodName.startsWith("patch") || methodName.startsWith("update")) {
            httpMethod = "PATCH";
        } else if (methodName.startsWith("delete")) {
            httpMethod = "DELETE";
        }
        return httpMethod + " " + className + "#" + methodName;
    }

    private String inferOperation(String endpoint) {
        if (endpoint.contains("batch-delete")) return "BATCH_DELETE";
        if (endpoint.startsWith("POST")) return "CREATE";
        if (endpoint.startsWith("PATCH")) return "UPDATE";
        if (endpoint.startsWith("DELETE")) return "DELETE";
        return "QUERY";
    }
}
