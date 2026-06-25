package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.service.mail.MailService;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import io.gitee.lujianshi.bidwinningbackend.utils.ResourceSearcher;
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
 * 通用 CRUD Service 基类。封装 7 个标准方法（add / update / delete / getById / listAll / deleteByIds / selectByIds），
 * 写方法统一加 {@code @Transactional(rollbackFor = Exception.class)}。
 * 子类实现 {@link #getMapper()} 和 {@link #getIdExtractor()}。
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

    /** 邮件主题前缀（子类 override，如 "[经办人创建]" / "[定标通知]"）。 */
    protected String emailSubjectPrefix = "[数据操作通知]";

    /** 操作类型（子类 override，如 CREATE / UPDATE / DELETE）。 */
    protected String emailOperation = "OPERATION";

    /** 端点描述（子类 override，如 "POST BidInfoController#create"）。 */
    protected String emailEndpoint = "ENDPOINT";

    protected void sendEmail(T entity, String targetEmail, String endpoint, String operation) {
        if (targetEmail == null || targetEmail.isBlank()) return;

        final String formName = endpoint;
        final String actionName = operation;
        final String operateTime = LocalDateTime.now().format(TS_FMT);
        final String status = operation + " 成功";
        final String subject = emailSubjectPrefix + " " + endpoint + " - " + operateTime;

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
                context.setVariable("data", finalDataList);
                String html = templateEngine.process("crud-email-template", context);
                mailService.sendHtmlEmail(targetEmail, subject, html);
                System.out.println("[BASE-EMAIL] 发送成功: to=" + targetEmail);
            } catch (Exception e) {
                System.err.println("[BASE-EMAIL] 发送失败: to=" + targetEmail + ", error=" + e.getMessage());
            }
        }, 0);
    }

    /**
     * 反射从 entity.targetEmail 读取收件人并发邮件；字段不存在则跳过。
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

    /**
     * 发送批量操作汇总邮件。{@code outerTargetEmail} 为空/blank 或 entities 为空时跳过。
     */
    protected void sendBatchEmail(List<T> entities, String outerTargetEmail, String endpoint, String operation) {
        if (outerTargetEmail == null || outerTargetEmail.isBlank()) return;
        if (entities == null || entities.isEmpty()) return;

        final String formName = endpoint;
        final String actionName = operation;
        final String operateTime = LocalDateTime.now().format(TS_FMT);
        final String status = operation + " 成功";
        final String subject = emailSubjectPrefix + " " + endpoint + " - " + operateTime;

        List<List<Map.Entry<String, Object>>> batchDataList = new ArrayList<>();
        for (T entity : entities) {
            try {
                Map<String, Object> dataMap = new LinkedHashMap<>();
                PropertyDescriptor[] pds = BeanUtils.getPropertyDescriptors(entity.getClass());
                for (PropertyDescriptor pd : pds) {
                    if (pd.getReadMethod() != null && !"class".equals(pd.getName()) && !"targetEmail".equals(pd.getName())) {
                        dataMap.put(pd.getName(), pd.getReadMethod().invoke(entity));
                    }
                }
                batchDataList.add(new ArrayList<>(dataMap.entrySet()));
            } catch (Exception e) {
                batchDataList.add(List.of());
            }
        }

        List<List<Map.Entry<String, Object>>> finalBatchData = batchDataList;
        AsyncTaskManager.execute(() -> {
            try {
                resourceSearcher.validateTemplateExists("crud-email-template");
                Context context = new Context();
                context.setVariable("subject", subject);
                context.setVariable("formName", formName);
                context.setVariable("actionName", actionName);
                context.setVariable("operateTime", operateTime);
                context.setVariable("status", status);
                context.setVariable("isBatch", true);
                context.setVariable("batchData", finalBatchData);
                String html = templateEngine.process("crud-email-template", context);
                mailService.sendHtmlEmail(outerTargetEmail, subject, html);
                System.out.println("[BASE-EMAIL] 批量发送成功: to=" + outerTargetEmail + ", count=" + entities.size());
            } catch (Exception e) {
                System.err.println("[BASE-EMAIL] 批量发送失败: to=" + outerTargetEmail + ", error=" + e.getMessage());
            }
        }, 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public int add(T entity) {
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
