package io.gitee.lujianshi.bidwinningbackend.aspect;

import com.alibaba.fastjson2.JSON;
import io.gitee.lujianshi.bidwinningbackend.annotation.SendEmailOnSuccess;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.service.mail.MailService;
import io.gitee.lujianshi.bidwinningbackend.service.mail.ResourceSearcher;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CRUD 操作成功后异步发送邮件通知切面
 *
 * <p>拦截所有标注 {@link SendEmailOnSuccess} 的 Controller 方法，在正常返回后异步发邮件。
 * 邮件发送、模板渲染、错误隔离均在异步子线程完成，不阻塞主业务响应。</p>
 *
 * <p>完整 5 步主线程 + 4 步异步子线程的执行链路、endpoint/operation 推断规则、收件人优先级
 * 等详见 {@code docs/aop-execution-chain.md}。</p>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.annotation.SendEmailOnSuccess
 * @see io.gitee.lujianshi.bidwinningbackend.service.mail.MailService
 * @see io.gitee.lujianshi.bidwinningbackend.service.mail.TemplateFiller
 * @see io.gitee.lujianshi.bidwinningbackend.service.mail.ResourceSearcher
 */
@Aspect
@Component
public class CrudResultEmailAspect {

    @Resource private SpringTemplateEngine templateEngine;
    @Resource private ResourceSearcher staticResourceSearcher;
    @Resource private MailService mailService;

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * <h2>拦截 @SendEmailOnSuccess 注解方法，异步发邮件</h2>
     *
     * <p>主线程 ~微秒级完成（反射推断 + 序列化 + 提交异步任务），
     * 模板校验、HTML 渲染、SMTP 发送均在延迟 3 秒后的后台子线程执行。</p>
     *
     * <p>收件人解析优先级：{@code Target-Email-Address} HTTP Header > 注解 {@code to()} > 配置默认。</p>
     *
     * @param joinPoint 当前执行方法的连接点（Spring AOP 自动注入）
     * @param result 被拦截方法的返回值（{@code returning = "result"} 绑定）
     * @param anno 被拦截方法上的注解实例（{@code argNames} 绑定）
     */
    @AfterReturning(
            pointcut = "@annotation(anno)",
            returning = "result",
            argNames = "joinPoint, result, anno"
    )
    public void handleAfterReturning(JoinPoint joinPoint, Object result,
                                     SendEmailOnSuccess anno) {
        try {
            if (!(result instanceof Result<?>)) {
                System.err.println("[CRUD-EMAIL] 返回值非 Result 类型，跳过: "
                        + joinPoint.getSignature());
                return;
            }
            Result<?> r = (Result<?>) result;

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String endpoint = getEndpoint(method);
            String operation = inferOperation(endpoint);
            String resultDataJson = JSON.toJSONString(r.getData());

            String subject = anno.subject().isEmpty()
                    ? String.format("[业务操作通知] %s %s - %s",
                            endpoint, operation,
                            LocalDateTime.now().format(TS_FMT))
                    : anno.subject();

            final String finalTo = resolveRecipient(anno);
            final String templateName = anno.templateName();
            final String finalSubject = subject;
            final String finalOperation = operation;
            final String finalEndpoint = endpoint;
            final String finalResultCode = String.valueOf(r.getCode());
            final String finalResultMessage = r.getMessage();
            final String finalResultDataJson = resultDataJson;

            AsyncTaskManager.execute(() -> {
                try {
                    staticResourceSearcher.validateTemplateExists(templateName);
                    Context context = new Context();
                    context.setVariable("subject", finalSubject);
                    context.setVariable("endpoint", finalEndpoint);
                    context.setVariable("operation", finalOperation);
                    context.setVariable("timestamp", LocalDateTime.now().format(TS_FMT));
                    context.setVariable("resultCode", finalResultCode);
                    context.setVariable("resultMessage", finalResultMessage);
                    context.setVariable("resultDataJson", finalResultDataJson);
                    String htmlContent = templateEngine.process(templateName, context);
                    mailService.sendHtmlEmail(finalTo, finalSubject, htmlContent);
                    System.out.println("[CRUD-EMAIL] 发送成功: to=" + finalTo);
                } catch (Exception e) {
                    System.err.println("[CRUD-EMAIL] 发送失败: to=" + finalTo
                            + ", error=" + e.getMessage());
                }
            }, 3000);

        } catch (Exception e) {
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

    private String resolveRecipient(SendEmailOnSuccess anno) {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            String headerEmail = request.getHeader("Target-Email-Address");
            if (headerEmail != null && !headerEmail.isBlank()) {
                return headerEmail;
            }
        } catch (IllegalStateException e) {
            // 非 HTTP 上下文（如测试）忽略，fallback 到注解 to()
        }
        return anno.to();
    }
}
