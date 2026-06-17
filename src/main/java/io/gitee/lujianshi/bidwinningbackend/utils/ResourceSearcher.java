package io.gitee.lujianshi.bidwinningbackend.utils;

import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Arrays;

/**
 * 静态资源与模板搜索服务
 *
 * 职责：
 * - 验证模板文件是否存在于 classpath:/templates/ 目录（精确匹配）
 * - 为 TemplateFiller 提供模板存在性校验
 *
 * 设计说明：
 * - validateTemplateExists() 负责精确搜索 templates/ 目录，不存在则抛异常（不 fallback）
 * - ResourcePatternResolver 在 {@code @Resource} 注入时自动使用 PathMatchingResourcePatternResolver 实现
 */
@Service
public class ResourceSearcher {

    @jakarta.annotation.Resource
    private ResourcePatternResolver resourcePatternResolver;

    /**
     * 验证模板文件是否存在（匹配 classpath:/templates/ 目录）
     *
     * 搜索方式：
     * - 通过 ResourcePatternResolver.getResources("classpath:/templates/*.html") 扫描 templates/ 下所有 HTML 文件
     * - 遍历扫描结果，提取文件名（去 .html 后缀），与 templateCode 精确匹配
     *
     * 行为：
     * - 找到匹配 → 方法正常返回
     * - 未找到 → 抛出 IllegalArgumentException("模板不存在: " + templateCode)
     * - 搜索过程出错 → 抛出 IllegalArgumentException("模板搜索失败: " + templateCode, e)
     *
     * @param templateCode 模板代码（不含 .html 后缀，例如 "bid-winning-notification"）
     * @throws IllegalArgumentException 模板文件不存在或搜索失败时抛出
     */

    public void validateTemplateExists(String templateCode) {
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:/templates/*.html");
            boolean exists = Arrays.stream(resources)
                    .map(this::extractTemplateName)
                    .anyMatch(name -> name != null && name.equals(templateCode));
            if (!exists) {
                throw new IllegalArgumentException("模板不存在: " + templateCode);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("模板搜索失败: " + templateCode, e);
        } finally {
            System.out.println("模板搜索完成: " + templateCode);
        }
    }

    /**
     * 从 Spring Resource 对象提取模板文件名（不含 .html 后缀）
     *
     * 提取逻辑：
     * - resource.getURL().getPath() 返回完整路径
     * - lastIndexOf("/templates/") 找到 templates/ 位置，截取后半部分
     * - replace(".html", "") 去掉后缀，得到 "bid-winning-notification"
     *
     * @param resource Spring 封装的资源文件对象
     * @return 模板文件名（不含 .html 后缀），解析失败返回 null
     */
    private String extractTemplateName(Resource resource) {
        try {
            String path = resource.getURL().getPath();
            int idx = path.lastIndexOf("/templates/");
            if (idx < 0) return null;
            return path.substring(idx + "/templates/".length()).replace(".html", "");
        } catch (IOException e) {
            return null;
        }
    }
}