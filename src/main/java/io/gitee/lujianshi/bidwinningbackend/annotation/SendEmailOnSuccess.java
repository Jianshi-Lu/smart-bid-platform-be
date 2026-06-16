package io.gitee.lujianshi.bidwinningbackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务操作成功后发送邮件通知（标记方法）
 *
 * <h3>用法</h3>
 * <pre>
 *   &#064;SendEmailOnSuccess(to = "ops&#064;company.com")
 *   &#064;PostMapping("/xxx")
 *   public Result&lt;X&gt; create(...) { ... }
 * </pre>
 *
 * <h3>触发时机</h3>
 * 由 {@code CrudResultEmailAspect} 拦截：
 * <ul>
 *   <li>方法正常返回（非异常）后触发</li>
 *   <li>异步发送（不阻塞业务响应）</li>
 *   <li>邮件失败不影响业务（try-catch 兜底）</li>
 * </ul>
 *
 * <h3>设计要点（为什么需要 @Target(METHOD) + @Retention(RUNTIME)）</h3>
 * <ul>
 *   <li><b>{@code @Target(ElementType.METHOD)}</b>：限制注解只能贴在方法上
 *     <ul>
 *       <li>不能贴在类/字段/参数上（语义错误）</li>
 *       <li>编译期 IDE 即可拦截误用</li>
 *     </ul>
 *   </li>
 *   <li><b>{@code @Retention(RetentionPolicy.RUNTIME)}</b>：<b>必须 RUNTIME</b>，否则 Spring AOP 反射读不到
 *     <ul>
 *       <li>CLASS 策略：编译进 .class 文件，但 JVM 加载时丢弃 → AOP 失效</li>
 *       <li>SOURCE 策略：编译器丢弃，运行时彻底无 → AOP 失效</li>
 *       <li>只有 RUNTIME 才能让 Spring 通过反射（{@code Method.getAnnotation(...)}）读取到注解实例</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>为什么注解本身不会"自动执行"</h3>
 * <ul>
 *   <li>注解只是"静态标记"（元数据），<b>没有任何执行能力</b></li>
 *   <li>生效靠 Spring AOP 动态代理机制 + Bean 生命周期</li>
 *   <li>Spring 启动时：看到 {@code @Aspect} 标记的切面类 → 记下 {@code pointcut} 规则</li>
 *   <li>运行时：看到本注解 → 生成"代理对象"（类比"秘书"）→ 拦截方法执行 → 触发切面逻辑</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SendEmailOnSuccess {

    /** 收件人邮箱（必填） */
    String to();

    /** 邮件主题（默认 "[业务操作通知] {endpoint} {operation} - {timestamp}"） */
    String subject() default "";

    /** Thymeleaf 模板文件名（不含 .html 后缀，默认 crud-result-notification） */
    String templateName() default "crud-result-notification";
}
