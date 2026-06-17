package io.gitee.lujianshi.bidwinningbackend.service;

import com.alibaba.fastjson2.JSON;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.service.mail.MailService;
import io.gitee.lujianshi.bidwinningbackend.service.mail.ResourceSearcher;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import jakarta.annotation.Resource;
import org.springframework.transaction.annotation.Transactional;

import org.thymeleaf.context.Context;
import org.thymeleaf.TemplateEngine;

import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * 通用 CRUD Service 基类。
 * <p>封装 7 个标准业务方法（create / update / delete / getById / listAll / deleteByIds），
 * 写方法统一加 {@code @Transactional(rollbackFor = Exception.class)}。
 * 子类只需实现 {@link #getMapper()} 和 {@link #getIdExtractor()} 两个抽象方法。</p>
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;T&gt;                         ← 本类，泛型 T = Entity 类型
 *     ↑ extends
 *   BidHandlerInfoService                  ← 经办人业务
 *   BidWinningNotificationService          ← 定标通知业务（重写 create 设 createdAt）
 *   BidWinningSupplierDetailService        ← 中标明细业务
 * </pre>
 *
 * <h3>调用链路</h3>
 * <pre>
 *   Controller → Service（本类方法）→ Mapper 接口（BaseMapper&lt;T&gt;）→ Mapper XML → MySQL
 * </pre>
 *
 * @param <T> 实体类型
 */
public abstract class BaseService<T> {
    protected abstract BaseMapper<T> getMapper();

    protected abstract Function<T, Integer> getIdExtractor();

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    protected SpringTemplateEngine templateEngine;

    @Resource
    protected ResourceSearcher resourceSearcher;

    @Resource
    protected MailService mailService;

    // ====== 子类 override ======

    /** 邮件主题前缀（子类 override，如 "[经办人创建]" / "[定标通知]"） */
    protected String emailSubjectPrefix = "[业务操作通知]";

    /** 操作类型（子类 override，如 CREATE / UPDATE / DELETE） */
    protected String emailOperation = "OPERATION";

    /** 端点描述（子类 override，如 "POST BidInfoController#create"） */
    protected String emailEndpoint = "ENDPOINT";

    // ==========================================

    protected void sendEmail(T entity, String targetEmail, String endpoint, String operation) {
        if (targetEmail == null || targetEmail.isBlank()) return;

        final String finalResultDataJson = JSON.toJSONString(entity);
        final String finalSubject = emailSubjectPrefix + " " + emailEndpoint
                + " - " + LocalDateTime.now().format(TS_FMT);
        final String finalOperation = emailOperation;
        final String finalEndpoint = emailEndpoint;

        AsyncTaskManager.execute(() -> {
            try {
                resourceSearcher.validateTemplateExists("crud-result-notification");
                Context context = new Context();
                // 固定字段
                context.setVariable("subject", finalSubject);
                context.setVariable("endpoint", finalEndpoint);
                context.setVariable("operation", finalOperation);
                context.setVariable("timestamp", LocalDateTime.now().format(TS_FMT));
                context.setVariable("resultCode", "200");
                context.setVariable("resultMessage", finalOperation + " 成功");
                context.setVariable("resultDataJson", finalResultDataJson);

                // 动态将 entity 所有字段加入 context，模板可通用访问
                java.beans.PropertyDescriptor[] pds =
                        org.springframework.beans.BeanUtils.getPropertyDescriptors(entity.getClass());
                for (java.beans.PropertyDescriptor pd : pds) {
                    if (pd.getReadMethod() != null && !"class".equals(pd.getName())) {
                        Object value = pd.getReadMethod().invoke(entity);
                        context.setVariable(pd.getName(), value);
                    }
                }

                // 渲染模版，调用邮件发送接口发送拼接好的html
                String html = templateEngine.process("crud-result-notification", context);
                mailService.sendHtmlEmail(targetEmail, finalSubject, html);
                System.out.println("[BASE-EMAIL] 发送成功: to=" + targetEmail);
            } catch (Exception e) {
                System.err.println("[BASE-EMAIL] 发送失败: to=" + targetEmail + ", error=" + e.getMessage());
            }
        }, 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public int create(T entity) {
        return getMapper().insert(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public int update(T entity) {
        return getMapper().updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return getMapper().deleteById(id);
    }

    public T getById(Integer id) {
        return getMapper().selectById(id);
    }

    public List<T> listAll() {
        return getMapper().selectAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<T> deleteByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<T> existing = getMapper().selectByIds(ids);
        if (!existing.isEmpty()) {
            List<Integer> existingIds = existing.stream()
                    .map(getIdExtractor())
                    .toList();
            getMapper().deleteByIds(existingIds);
        }
        return existing;
    }
}
