package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.service.mail.MailService;
import io.gitee.lujianshi.bidwinningbackend.utils.ResourceSearcher;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;

import org.thymeleaf.context.Context;

import org.thymeleaf.spring6.SpringTemplateEngine;

import java.beans.PropertyDescriptor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   Controller → Service（本类方法）→ Mapper 接口（BaseMapper&lt;T&gt; ）→ Mapper XML → MySQL
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
    protected String emailSubjectPrefix = "[数据操作通知]";

    /** 操作类型（子类 override，如 CREATE / UPDATE / DELETE） */
    protected String emailOperation = "OPERATION";

    /** 端点描述（子类 override，如 "POST BidInfoController#create"） */
    protected String emailEndpoint = "ENDPOINT";

    // ==========================================
    protected void sendEmail(T entity, String targetEmail, String endpoint, String operation) {
        if (targetEmail == null || targetEmail.isBlank()) return;

        final String formName = endpoint;
        final String actionName = operation;
        final String operateTime = LocalDateTime.now().format(TS_FMT);                     // 操作时间
        final String status = operation + " 成功";                                         // 执行状态
        final String subject = emailSubjectPrefix + " " + endpoint + " - " + operateTime; // 邮件主题

        // 构造 data 列表（List<Map.Entry<String, Object>>），供模板动态遍历
        List<Map.Entry<String, Object>> dataList;
        try {
            Map<String, Object> dataMap = new LinkedHashMap<>();
            PropertyDescriptor[] pds = BeanUtils.getPropertyDescriptors(entity.getClass());
            for (PropertyDescriptor pd : pds) {
                if (pd.getReadMethod() != null && !"class".equals(pd.getName()) && !"targetEmail".equals(pd.getName())) {
                    dataMap.put(pd.getName(), pd.getReadMethod().invoke(entity));
                }
            }
            dataList = new ArrayList<>(dataMap.entrySet());
        } catch (Exception e) {
            dataList = List.of();
        }

        List<Map.Entry<String, Object>> finalDataList = dataList;
        AsyncTaskManager.execute(() -> {
            try {
                resourceSearcher.validateTemplateExists("crud-email-template");
                Context context = new Context();
                context.setVariable("subject", subject);
                context.setVariable("formName", formName);
                context.setVariable("actionName", actionName);
                context.setVariable("operateTime", operateTime);
                context.setVariable("status", status);
                context.setVariable("data", finalDataList);     // 返回数据
                String html = templateEngine.process("crud-email-template", context);
                mailService.sendHtmlEmail(targetEmail, subject, html);
                System.out.println("[BASE-EMAIL] 发送成功: to=" + targetEmail);
            } catch (Exception e) {
                System.err.println("[BASE-EMAIL] 发送失败: to=" + targetEmail + ", error=" + e.getMessage());
            }
        }, 0);
    }

    /**
     * sendEmail 重载——targetEmail 从 entity.targetEmail 字段反射读取。
     * entity 需携带 targetEmail 字段（已落库），否则跳过发邮件。
     */
    protected void sendEmail(T entity, String endpoint, String operation) {
        String targetEmail = null;
        try {
            PropertyDescriptor pd = new PropertyDescriptor("targetEmail", entity.getClass());
            Object val = pd.getReadMethod().invoke(entity);
            targetEmail = (val != null) ? val.toString() : null;
        } catch (Exception e) {
            // targetEmail 字段不存在 → 跳过
        }
        this.sendEmail(entity, targetEmail, endpoint, operation);
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
